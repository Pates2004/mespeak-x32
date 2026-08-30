/* Synchronous Android backend for the legacy eSpeak library. */

#include <stddef.h>
#include <stdint.h>
#include <sys/time.h>
#include <time.h>

#include "speech.h"
#include "event.h"
#include "fifo.h"
#include "wave.h"

void event_init(void) {}
void event_set_callback(t_espeak_callback *) {}
espeak_ERROR event_clear_all(void) { return EE_OK; }
espeak_ERROR event_declare(espeak_EVENT *) { return EE_OK; }
void event_terminate(void) {}

void fifo_init(void) {}
espeak_ERROR fifo_add_command(t_espeak_command *) { return EE_INTERNAL_ERROR; }
espeak_ERROR fifo_add_commands(t_espeak_command *, t_espeak_command *) { return EE_INTERNAL_ERROR; }
espeak_ERROR fifo_stop(void) { return EE_OK; }
int fifo_is_busy(void) { return 0; }
void fifo_terminate(void) {}
int fifo_is_command_enabled(void) { return 1; }

void wave_init(int) {}
void *wave_open(const char *) { return (void *)1; }
size_t wave_write(void *, char *, size_t size) { return size; }
int wave_close(void *) { return 0; }
void wave_flush(void *) {}
int wave_is_busy(void *) { return 0; }
void wave_terminate(void) {}
uint32_t wave_get_read_position(void *) { return 0; }
uint32_t wave_get_write_position(void *) { return 0; }
int wave_get_remaining_time(uint32_t, uint32_t *time) {
    if (time) *time = 0;
    return time ? 0 : -1;
}
void wave_set_callback_is_output_enabled(t_wave_callback *) {}
void *wave_test_get_write_buffer(void) { return NULL; }

void clock_gettime2(struct timespec *ts) {
    if (!ts) return;
    struct timeval tv;
    gettimeofday(&tv, NULL);
    ts->tv_sec = tv.tv_sec;
    ts->tv_nsec = tv.tv_usec * 1000;
}

void add_time_in_ms(struct timespec *ts, int time_in_ms) {
    if (!ts) return;
    int64_t ns = (int64_t)ts->tv_nsec + (int64_t)time_in_ms * 1000000;
    ts->tv_sec += ns / 1000000000;
    ts->tv_nsec = ns % 1000000000;
}
