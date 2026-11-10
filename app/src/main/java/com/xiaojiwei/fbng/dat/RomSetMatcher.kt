package com.xiaojiwei.fbng.dat

data class ZipMember(
    val name: String,
    val size: Long,
    val crc: Long,
)

enum class ProblemKind {
    MISSING,
    CRC_MISMATCH,
    SIZE_MISMATCH,
    NAME_MISMATCH,
    DUPLICATE_NAME,
    EXTRA,
}

data class RomProblem(
    val kind: ProblemKind,
    val expectedName: String? = null,
    val actualName: String? = null,
    val expectedSize: Long? = null,
    val actualSize: Long? = null,
    val expectedCrc: Long? = null,
    val actualCrc: Long? = null,
)

data class SetMatch(
    val setName: String,
    val description: String,
    val isBios: Boolean,
    val requiredCount: Int,
    val matchedOwnCount: Int,
    val contentHits: Int,
    val explainedCount: Int,
    val problems: List<RomProblem>,
) {
    val isClean: Boolean get() = problems.none { it.kind != ProblemKind.EXTRA }
}

data class Identification(
    val best: SetMatch?,
) {
    val recognized: Boolean get() = best != null
}

object RomSetMatcher {
    fun identify(members: List<ZipMember>, catalog: DatCatalog): Identification {
        if (members.isEmpty()) return Identification(null)
        val duplicateNames = members.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        val unique = members.filter { it.name !in duplicateNames }
        val byName = unique.associateBy { it.name }

        val candidateNames = LinkedHashSet<String>()
        for (member in members) {
            catalog.gamesByOwnName[member.name]?.let(candidateNames::addAll)
            catalog.gamesByOwnCrc[member.crc]?.let(candidateNames::addAll)
        }
        if (candidateNames.isEmpty()) return Identification(null)

        val evaluated = candidateNames.map { name ->
            evaluate(catalog.byName.getValue(name), members, byName, duplicateNames, catalog)
        }
        val clean = evaluated.filter { it.isClean }
        if (clean.isNotEmpty()) {
            return Identification(
                clean.maxWith(compareBy<SetMatch> { it.explainedCount }.thenBy { it.matchedOwnCount }),
            )
        }

        val ranked = evaluated.sortedByDescending { it.contentHits }
        val top = ranked.first()
        val secondHits = ranked.getOrNull(1)?.contentHits ?: -1
        val majority = top.requiredCount > 0 && top.contentHits * 2 >= top.requiredCount
        val clearWinner = top.contentHits >= 2 && top.contentHits > secondHits
        val almost = top.requiredCount > 0 && top.contentHits >= top.requiredCount - 1 && top.contentHits > secondHits
        val onlyCandidate = secondHits < 0 && top.contentHits >= 2
        return if (top.contentHits > 0 && (majority || clearWinner || almost || onlyCandidate)) {
            Identification(top)
        } else {
            Identification(null)
        }
    }

    private fun evaluate(
        game: DatGame,
        members: List<ZipMember>,
        byName: Map<String, ZipMember>,
        duplicateNames: Set<String>,
        catalog: DatCatalog,
    ): SetMatch {
        val own = catalog.ownRoms(game)
        val inherited = catalog.inheritedRoms(game)
        val problems = mutableListOf<RomProblem>()
        val used = HashSet<ZipMember>()
        duplicateNames.forEach { name ->
            problems += RomProblem(kind = ProblemKind.DUPLICATE_NAME, actualName = name)
        }

        var matchedOwn = 0
        var nameMismatches = 0
        for (rom in own) {
            val named = byName[rom.name]
            when {
                named == null -> {
                    val renamed = members.firstOrNull { member ->
                        member !in used &&
                            member.name !in duplicateNames &&
                            member.crc == rom.crc &&
                            member.size == rom.size
                    }
                    if (renamed != null) {
                        used += renamed
                        nameMismatches++
                        problems += RomProblem(
                            kind = ProblemKind.NAME_MISMATCH,
                            expectedName = rom.name,
                            actualName = renamed.name,
                            expectedSize = rom.size,
                            actualSize = renamed.size,
                            expectedCrc = rom.crc,
                            actualCrc = renamed.crc,
                        )
                    } else {
                        problems += RomProblem(
                            kind = ProblemKind.MISSING,
                            expectedName = rom.name,
                            expectedSize = rom.size,
                            expectedCrc = rom.crc,
                        )
                    }
                }
                named.size != rom.size -> {
                    used += named
                    problems += RomProblem(
                        kind = ProblemKind.SIZE_MISMATCH,
                        expectedName = rom.name,
                        actualName = named.name,
                        expectedSize = rom.size,
                        actualSize = named.size,
                        expectedCrc = rom.crc,
                        actualCrc = named.crc,
                    )
                }
                named.crc != rom.crc -> {
                    used += named
                    problems += RomProblem(
                        kind = ProblemKind.CRC_MISMATCH,
                        expectedName = rom.name,
                        actualName = named.name,
                        expectedSize = rom.size,
                        actualSize = named.size,
                        expectedCrc = rom.crc,
                        actualCrc = named.crc,
                    )
                }
                else -> {
                    used += named
                    matchedOwn++
                }
            }
        }

        for (rom in inherited) {
            val named = byName[rom.name] ?: continue
            if (named in used) continue
            used += named
            if (named.size != rom.size) {
                problems += RomProblem(
                    kind = ProblemKind.SIZE_MISMATCH,
                    expectedName = rom.name,
                    actualName = named.name,
                    expectedSize = rom.size,
                    actualSize = named.size,
                    expectedCrc = rom.crc,
                    actualCrc = named.crc,
                )
            } else if (named.crc != rom.crc) {
                problems += RomProblem(
                    kind = ProblemKind.CRC_MISMATCH,
                    expectedName = rom.name,
                    actualName = named.name,
                    expectedSize = rom.size,
                    actualSize = named.size,
                    expectedCrc = rom.crc,
                    actualCrc = named.crc,
                )
            }
        }

        for (member in members) {
            if (member in used || member.name in duplicateNames) continue
            problems += RomProblem(kind = ProblemKind.EXTRA, actualName = member.name, actualSize = member.size, actualCrc = member.crc)
        }

        return SetMatch(
            setName = game.name,
            description = game.description,
            isBios = game.isBios,
            requiredCount = own.size,
            matchedOwnCount = matchedOwn,
            contentHits = matchedOwn + nameMismatches,
            explainedCount = used.size,
            problems = problems,
        )
    }
}

fun crcHex(crc: Long): String = "%08x".format(crc and 0xffff_ffffL)
