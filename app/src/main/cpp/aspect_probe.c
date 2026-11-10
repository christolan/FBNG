#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <math.h>

#define LOG_TAG "FBNG"

/*
 * LibretroDroid 0.13.2 keeps the fitted aspect inside the native singleton and
 * does not expose it to Java. These are the Itanium names of
 * LibretroDroid::getAspectRatio() and the function-local singleton in
 * LibretroDroid::getInstance(). A miss returns NaN; the UI then uses 4:3 and
 * still does not inset the game.
 */
#define ASPECT_SYMBOL "_ZN13libretrodroid13LibretroDroid14getAspectRatioEv"
#define INSTANCE_SYMBOL "_ZZN13libretrodroid13LibretroDroid11getInstanceEvE8instance"

typedef float (*AspectFn)(void *self);

static AspectFn aspect_fn = NULL;
static void *aspect_self = NULL;
static int unresolved = 0;
static int logged_missing = 0;

static void resolve_aspect(void) {
    void *handle;
    if (aspect_fn != NULL || unresolved) {
        return;
    }
    handle = dlopen("liblibretrodroid.so", RTLD_NOW | RTLD_NOLOAD);
    if (handle == NULL) {
        if (!logged_missing) {
            __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "liblibretrodroid.so is not loaded: %s", dlerror());
            logged_missing = 1;
        }
        return;
    }
    aspect_self = dlsym(handle, INSTANCE_SYMBOL);
    aspect_fn = (AspectFn) dlsym(handle, ASPECT_SYMBOL);
    if (aspect_self == NULL || aspect_fn == NULL) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "core aspect symbols missing: %s", dlerror());
        aspect_fn = NULL;
        aspect_self = NULL;
        unresolved = 1;
    }
}

JNIEXPORT jfloat JNICALL
Java_com_xiaojiwei_fbng_game_CoreGeometry_nativeAspectRatio(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    resolve_aspect();
    if (aspect_fn == NULL || aspect_self == NULL) {
        return nanf("");
    }
    return aspect_fn(aspect_self);
}
