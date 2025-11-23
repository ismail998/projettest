import pyaudio

SAMPLE_RATE = 16000
CHANNELS = 1
FORMAT = pyaudio.paInt16
CHUNK = 2048  # buffer size

audio = pyaudio.PyAudio()

# ميكروفون
stream_in = audio.open(
    format=FORMAT,
    channels=CHANNELS,
    rate=SAMPLE_RATE,
    input=True,
    frames_per_buffer=CHUNK
)

# سماعات
stream_out = audio.open(
    format=FORMAT,
    channels=CHANNELS,
    rate=SAMPLE_RATE,
    output=True,
    frames_per_buffer=CHUNK
)

print("🎤 Speak now... (CTRL + C to stop)")

try:
    while True:
        data = stream_in.read(CHUNK, exception_on_overflow=False)
        stream_out.write(data)
except KeyboardInterrupt:
    print("\nStopping...")
finally:
    stream_in.stop_stream()
    stream_out.stop_stream()
    stream_in.close()
    stream_out.close()
    audio.terminate()
