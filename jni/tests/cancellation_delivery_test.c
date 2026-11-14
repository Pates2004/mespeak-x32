/* Bounded JNI-boundary regression fixture. The real binding and libsonic are
 * compiled below; the JVM and core producer are deterministic test doubles.
 * Full text/voice recovery is covered separately by Android instrumentation. */
#include <stdarg.h>
#include <stdint.h>

#include "../jni/eSpeakService.c"

#define CHECK(condition) do { if (!(condition)) { \
  fprintf(stderr, "Check failed at line %d: %s\n", __LINE__, #condition); \
  return 1; } } while (0)

static int audio_calls;
static int word_calls;
static int canceled_after_audio;
static int canceled_after_word;
static int synth_calls;
static int core_callbacks;
static int reset_calls;
static int aborts;
static int accepted_bytes;
static int fail_get_utf;
static t_espeak_callback *core_callback;
static JNIEnv fake_env;

static jbyteArray FakeNewByteArray(JNIEnv *env, jsize length) {
  jsize *allocation = malloc(sizeof(jsize));
  if (allocation != NULL) *allocation = length;
  return (jbyteArray) allocation;
}
static void FakeSetByteArrayRegion(JNIEnv *env, jbyteArray array, jsize start,
    jsize length, const jbyte *bytes) { }
static void FakeDeleteLocalRef(JNIEnv *env, jobject object) { free(object); }
static jboolean FakeExceptionCheck(JNIEnv *env) { return JNI_FALSE; }
static const char *FakeGetStringUTFChars(JNIEnv *env, jstring text, jboolean *copy) {
  return fail_get_utf ? NULL : (const char *) text;
}
static void FakeReleaseStringUTFChars(JNIEnv *env, jstring text, const char *bytes) { }
static jint FakeAttachCurrentThread(JavaVM *vm, JNIEnv **env, void *arguments) {
  *env = &fake_env;
  return JNI_OK;
}
static void FakeCallVoidMethod(JNIEnv *env, jobject object, jmethodID method, ...) {
  if (method == METHOD_nativeSynthWordCallback) {
    word_calls++;
    if (canceled_after_word) {
      Java_com_reecedunn_espeak_SpeechSynthesis_nativeStop(env, object);
    }
  } else {
    audio_calls++;
    if (canceled_after_audio) {
      Java_com_reecedunn_espeak_SpeechSynthesis_nativeStop(env, object);
    } else {
      va_list arguments;
      va_start(arguments, method);
      jbyteArray audio = va_arg(arguments, jbyteArray);
      accepted_bytes += *(jsize *) audio;
      va_end(arguments);
    }
  }
}

void AndroidResetCanceledSynthesis(void) { reset_calls++; }
void espeak_SetSynthCallback(t_espeak_callback *callback) { core_callback = callback; }
espeak_ERROR espeak_Synchronize(void) { return EE_OK; }
espeak_ERROR espeak_Synth(const void *text, size_t size, unsigned int position,
    espeak_POSITION_TYPE position_type, unsigned int end_position,
    unsigned int flags, unsigned int *identifier, void *user_data) {
  synth_calls++;
  short input[6615];
  for (int sample = 0; sample < 6615; sample++) input[sample] = sample % 40 < 20 ? 1500 : -1500;
  espeak_EVENT events[2] = {0};
  events[0].type = espeakEVENT_WORD;
  events[0].text_position = 1;
  events[0].length = 8;
  events[0].user_data = user_data;
  events[1].type = espeakEVENT_LIST_TERMINATED;
  events[1].user_data = user_data;
  for (int buffer = 0; buffer < 3; buffer++) {
    core_callbacks++;
    if (core_callback(input, 6615, events) == SYNTH_ABORT) {
      aborts++;
      return EE_OK;
    }
  }
  core_callback(NULL, 0, &events[1]);
  return EE_OK;
}

static void reset_case(float compression) {
  sonic_destroy_stream();
  active_generation = atomic_load(&cancellation_generation);
  atomic_store(&sonic_failed, 0);
  sonic_speed = compression;
  audio_calls = word_calls = canceled_after_audio = canceled_after_word = 0;
  synth_calls = core_callbacks = reset_calls = aborts = accepted_bytes = 0;
  fail_get_utf = 0;
}

int main(void) {
  static const struct JNINativeInterface native_interface = {
    .NewByteArray = FakeNewByteArray,
    .SetByteArrayRegion = FakeSetByteArrayRegion,
    .DeleteLocalRef = FakeDeleteLocalRef,
    .ExceptionCheck = FakeExceptionCheck,
    .GetStringUTFChars = FakeGetStringUTFChars,
    .ReleaseStringUTFChars = FakeReleaseStringUTFChars,
    .CallVoidMethod = FakeCallVoidMethod
  };
  static const struct JNIInvokeInterface invoke_interface = {
    .AttachCurrentThread = FakeAttachCurrentThread
  };
  fake_env = &native_interface;
  JavaVM fake_vm = &invoke_interface;
  jvm = &fake_vm;
  METHOD_nativeSynthCallback = (jmethodID) (uintptr_t) 1;
  METHOD_nativeSynthWordCallback = (jmethodID) (uintptr_t) 2;
  const jstring text = (jstring) "Przycisk ustawienia sieci.";

  reset_case(1.0f);
  jint generation = Java_com_reecedunn_espeak_SpeechSynthesis_nativeGetCancellationGeneration(&fake_env, NULL);
  Java_com_reecedunn_espeak_SpeechSynthesis_nativeStop(&fake_env, NULL);
  CHECK(Java_com_reecedunn_espeak_SpeechSynthesis_nativeSynthesize(&fake_env, NULL,
      text, JNI_FALSE, generation) == REQUEST_CANCELED);
  CHECK(synth_calls == 0 && audio_calls == 0 && reset_calls == 1);

  reset_case(3.0f);
  generation = (jint) active_generation;
  sonic_stream = sonicCreateStream(22050, 1);
  CHECK(sonic_stream != NULL);
  fail_get_utf = 1;
  CHECK(Java_com_reecedunn_espeak_SpeechSynthesis_nativeSynthesize(&fake_env, NULL,
      text, JNI_FALSE, generation) == REQUEST_ERROR);
  CHECK(synth_calls == 0 && reset_calls == 1 && sonic_stream == NULL);

  reset_case(1.0f);
  generation = (jint) active_generation;
  Java_com_reecedunn_espeak_SpeechSynthesis_nativeResetCanceledSynthesis(&fake_env, NULL, generation);
  CHECK(reset_calls == 0);
  Java_com_reecedunn_espeak_SpeechSynthesis_nativeStop(&fake_env, NULL);
  Java_com_reecedunn_espeak_SpeechSynthesis_nativeResetCanceledSynthesis(&fake_env, NULL, generation);
  CHECK(reset_calls == 1);

  for (int sonic = 0; sonic < 2; sonic++) {
    reset_case(sonic ? 3.0f : 1.0f);
    generation = (jint) active_generation;
    canceled_after_audio = 1;
    CHECK(Java_com_reecedunn_espeak_SpeechSynthesis_nativeSynthesize(&fake_env, NULL,
        text, JNI_FALSE, generation) == REQUEST_CANCELED);
    CHECK(audio_calls == 1 && core_callbacks == 1 && aborts == 1);
    CHECK(accepted_bytes == 0 && reset_calls == 1 && !atomic_load(&sonic_failed));

    reset_case(sonic ? 3.0f : 1.0f);
    generation = (jint) active_generation;
    CHECK(Java_com_reecedunn_espeak_SpeechSynthesis_nativeSynthesize(&fake_env, NULL,
        text, JNI_FALSE, generation) == REQUEST_SUCCESS);
    CHECK(accepted_bytes > 0 && audio_calls > 0 && aborts == 0 && reset_calls == 0);
    CHECK(sonic_stream == NULL);
  }

  reset_case(1.0f);
  generation = (jint) active_generation;
  canceled_after_word = 1;
  CHECK(Java_com_reecedunn_espeak_SpeechSynthesis_nativeSynthesize(&fake_env, NULL,
      text, JNI_FALSE, generation) == REQUEST_CANCELED);
  CHECK(word_calls == 1 && audio_calls == 0 && core_callbacks == 1 && aborts == 1);

  reset_case(1.1f);
  sonic_stream = sonicCreateStream(22050, 1);
  CHECK(sonic_stream != NULL);
  sonicSetSpeed(sonic_stream, 1.1f);
  short buffered[30000];
  for (int sample = 0; sample < 30000; sample++) buffered[sample] = sample % 40 < 20 ? 1500 : -1500;
  CHECK(sonicWriteShortToStream(sonic_stream, buffered, 30000));
  CHECK(sonicSamplesAvailable(sonic_stream) > 8192);
  canceled_after_audio = 1;
  CHECK(deliver_sonic_output(&fake_env, NULL, 0) == DELIVERY_CANCELED);
  CHECK(audio_calls == 1);
  CHECK(deliver_sonic_output(&fake_env, NULL, 1) == DELIVERY_CANCELED);
  CHECK(audio_calls == 1);
  sonic_destroy_stream();

  puts("{\"checks\":\"preentry,allocation-cleanup,reset-generation,direct-delivery,sonic-delivery,word-cancel,next-request,drain-flush\",\"passed\":true}");
  return 0;
}
