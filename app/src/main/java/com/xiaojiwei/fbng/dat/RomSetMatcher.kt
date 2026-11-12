package com.xiaojiwei.fbng.dat

data class ZipMember(
    val name: String,
    val size: Long,
    val crc: Long,
) {
    internal val signature get() = RomSignature(size, crc)
}

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
    val ambiguous: Boolean = false,
)

object RomSetMatcher {
    fun identify(
        members: List<ZipMember>,
        catalog: DatCatalog,
        archiveName: String? = null,
    ): Identification {
        if (members.isEmpty()) return Identification(null)
        val byName = members.groupBy { it.name }
        val preferred = archiveName?.substringBeforeLast('.')?.lowercase()?.let(catalog.byName::get)
        val preferredMatch = preferred?.let { evaluate(it, members, byName, catalog) }
        if (preferredMatch?.isClean == true && preferredMatch.explainedCount > 0) {
            return Identification(preferredMatch)
        }

        val candidateNames = LinkedHashSet<String>()
        for (member in members) {
            catalog.gamesByOwnName[member.name]?.let(candidateNames::addAll)
            catalog.gamesByOwnCrc[member.crc]?.let(candidateNames::addAll)
        }
        val evaluated = candidateNames.map { name ->
            if (name == preferredMatch?.setName) preferredMatch
            else evaluate(catalog.byName.getValue(name), members, byName, catalog)
        }
        val byCoverage = compareBy<SetMatch> { it.explainedCount }.thenBy { it.matchedOwnCount }
        val clean = evaluated.filter { it.isClean }
        val bestClean = clean.maxWithOrNull(byCoverage)
        if (bestClean != null) {
            val tied = clean.count { byCoverage.compare(bestClean, it) == 0 } > 1
            return if (tied) Identification(null, ambiguous = true) else Identification(bestClean)
        }

        // A canonical filename still identifies a damaged set for the import report.
        if (preferredMatch != null && preferredMatch.contentHits > 0) return Identification(preferredMatch)
        val ranked = evaluated.sortedByDescending { it.contentHits }
        val top = ranked.firstOrNull() ?: return Identification(null)
        val secondHits = ranked.getOrNull(1)?.contentHits ?: -1
        val majority = top.requiredCount > 0 && top.contentHits * 2 >= top.requiredCount
        val clearWinner = top.contentHits >= 2 && top.contentHits > secondHits
        val almost = top.requiredCount > 0 && top.contentHits >= top.requiredCount - 1 && top.contentHits > secondHits
        return Identification(top.takeIf { it.contentHits > 0 && (majority || clearWinner || almost) })
    }

    private fun evaluate(
        game: DatGame,
        members: List<ZipMember>,
        byName: Map<String, List<ZipMember>>,
        catalog: DatCatalog,
    ): SetMatch {
        val own = catalog.ownRoms(game)
        val inherited = catalog.inheritedRoms(game)
        val problems = mutableListOf<RomProblem>()
        val used = HashSet<ZipMember>()
        var matchedOwn = 0
        var nameMismatches = 0
        for (rom in own) {
            val named = byName[rom.name].orEmpty()
            val exact = named.firstOrNull { it.size == rom.size && it.crc == rom.crc }
            if (exact != null) {
                used += exact
                matchedOwn++
                continue
            }

            val mismatch = named.firstOrNull { it !in used }
            val renamed = if (mismatch == null) members.firstOrNull {
                it !in used && it.size == rom.size && it.crc == rom.crc
            } else null
            if (renamed != null) {
                used += renamed
                nameMismatches++
                problems += problem(ProblemKind.NAME_MISMATCH, rom, renamed)
            } else if (mismatch != null) {
                used += mismatch
                problems += problem(
                    if (mismatch.size != rom.size) ProblemKind.SIZE_MISMATCH else ProblemKind.CRC_MISMATCH,
                    rom,
                    mismatch,
                )
            } else {
                problems += problem(ProblemKind.MISSING, rom, null)
            }
        }

        // FBNeo looks up inherited ROMs by CRC, including DAT merge aliases.
        for (rom in inherited) {
            val exact = members.firstOrNull { it.size == rom.size && it.crc == rom.crc }
            if (exact != null) {
                used += exact
                continue
            }
            val named = byName[rom.name]?.firstOrNull { it !in used } ?: continue
            used += named
            problems += problem(
                if (named.size != rom.size) ProblemKind.SIZE_MISMATCH else ProblemKind.CRC_MISMATCH,
                rom,
                named,
            )
        }

        for ((name, group) in byName) {
            // Some drivers legitimately use the same basename for different chips.
            if (group.size > 1 && (group.any { it !in used } || group.toSet().size != group.size)) {
                problems += RomProblem(ProblemKind.DUPLICATE_NAME, actualName = name)
            }
        }
        for (member in members) {
            if (member !in used) {
                problems += RomProblem(ProblemKind.EXTRA, actualName = member.name, actualSize = member.size, actualCrc = member.crc)
            }
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

    private fun problem(kind: ProblemKind, expected: DatRom, actual: ZipMember?) = RomProblem(
        kind = kind,
        expectedName = expected.name,
        actualName = actual?.name,
        expectedSize = expected.size,
        actualSize = actual?.size,
        expectedCrc = expected.crc,
        actualCrc = actual?.crc,
    )
}

fun crcHex(crc: Long): String = "%08x".format(crc and 0xffff_ffffL)
