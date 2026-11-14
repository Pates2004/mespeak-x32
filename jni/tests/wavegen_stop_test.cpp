// Exercise the real queue cleanup while observing ownership of voice copies.
#include <cstdio>
#include <cstdlib>
#include <cstring>

static int freed_voice_copies;
static void ReleaseVoiceCopy(void *memory)
{
    ++freed_voice_copies;
    std::free(memory);
}
#define free ReleaseVoiceCopy
#include "../../engine/src/wavegen.cpp"
#undef free

char mbrola_name[20] = {0};
void MbrolaReset(void) { }

#define CHECK(condition) do { if (!(condition)) { \
    std::fprintf(stderr, "Check failed at line %d: %s\n", __LINE__, #condition); \
    return 1; } } while (0)

int main()
{
    // Wrap around the ring and mix owning/nonowning command types.
    wcmdq_head = N_WCMDQ - 2;
    wcmdq_tail = 1;
    wcmdq[N_WCMDQ - 2][0] = WCMD_VOICE;
    wcmdq[N_WCMDQ - 2][1] = reinterpret_cast<intptr_t>(std::malloc(sizeof(voice_t)));
    wcmdq[N_WCMDQ - 1][0] = WCMD_PAUSE;
    wcmdq[0][0] = WCMD_VOICE;
    wcmdq[0][1] = reinterpret_cast<intptr_t>(std::malloc(sizeof(voice_t)));
    CHECK(wcmdq[N_WCMDQ - 2][1] && wcmdq[0][1]);
    wavegen_resume = 1;
    wavegen_echo_complete = 120;
    WcmdqStop();
    CHECK(freed_voice_copies == 2);
    CHECK(wcmdq_head == 0 && wcmdq_tail == 0 && WcmdqUsed() == 0);
    CHECK(wavegen_resume == 0 && wavegen_echo_complete == 0);
    for (int slot = 0; slot < N_WCMDQ; ++slot)
        for (int value = 0; value < 4; ++value)
            CHECK(wcmdq[slot][value] == 0);
    WcmdqStop();
    CHECK(freed_voice_copies == 2);
    std::puts("{\"checks\":\"pending-voice-ownership,queue-wrap,resume-echo-reset,idempotent-stop\",\"passed\":true}");
    return 0;
}
