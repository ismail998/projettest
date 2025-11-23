# server_pc.py
# Python 3.8+
# Requires: pyaudio
# Usage: python server_pc.py
# Listens on 0.0.0.0:5000 and expects raw PCM 16-bit mono 16000Hz from the Android app.
# It plays audio from the phone and simultaneously captures PC mic audio and sends it back through the same socket.

import socket
import threading
import signal
import sys
import pyaudio
import time

HOST = "0.0.0.0"   # استمع على كل الواجهات
PORT = 5000        # نفس المنفذ كما في كود الهاتف
SAMPLE_RATE = 16000
CHANNELS = 1
FORMAT = pyaudio.paInt16
FRAMES_PER_BUFFER = 2048  # مقدار البيانات لكل دورة (يجب أن يتوافق مع الهاتف)

p = pyaudio.PyAudio()
running = True
client_sock = None

def play_from_socket(conn):
    """ اقرأ خام byte PCM من الـ socket وشغله على سماعة الكمبيوتر """
    # إعداد تشغيل الصوت
    stream_out = p.open(format=FORMAT,
                        channels=CHANNELS,
                        rate=SAMPLE_RATE,
                        output=True,
                        frames_per_buffer=FRAMES_PER_BUFFER)

    try:
        while running:
            # نحاول قراءة نفس حجم البافر — يمكن أن يرجع أقل عند الإغلاق
            data = conn.recv(FRAMES_PER_BUFFER * 2)  # 2 bytes per sample for 16bit
            if not data:
                # الاتصال قُطع
                break
            # شغّل الصوت المستلم
            stream_out.write(data)
    except Exception as e:
        print("play_from_socket error:", e)
    finally:
        try:
            stream_out.stop_stream()
            stream_out.close()
        except:
            pass

def capture_and_send(conn):
    """ اقرأ مايك الكمبيوتر وأرسله عبر الـ socket للهاتف """
    # إعداد التقاط المايك
    stream_in = p.open(format=FORMAT,
                       channels=CHANNELS,
                       rate=SAMPLE_RATE,
                       input=True,
                       frames_per_buffer=FRAMES_PER_BUFFER)

    try:
        while running:
            try:
                data = stream_in.read(FRAMES_PER_BUFFER, exception_on_overflow=False)
            except Exception as e:
                # في حال overflow أو خطأ بسيط نكمل
                # تأخير بسيط لتقليل الحمل
                time.sleep(0.01)
                continue

            # أرسل بيانات الخام عبر نفس الاتصال
            try:
                conn.sendall(data)
            except Exception as e:
                print("Error sending mic -> socket:", e)
                break
    finally:
        try:
            stream_in.stop_stream()
            stream_in.close()
        except:
            pass

def handle_client(conn, addr):
    print(f"[+] Connection from {addr}")
    t1 = threading.Thread(target=play_from_socket, args=(conn,), daemon=True)
    t2 = threading.Thread(target=capture_and_send, args=(conn,), daemon=True)
    t1.start()
    t2.start()

    # انتظر حتى تنتهي أي منهما
    t1.join()
    t2.join()
    print(f"[-] Connection closed: {addr}")
    try:
        conn.close()
    except:
        pass

def main():
    global running, client_sock
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((HOST, PORT))
    srv.listen(1)
    print(f"Listening on {HOST}:{PORT} ... (waiting for Android client)")

    def sigint_handler(sig, frame):
        nonlocal srv
        print("\nStopping server...")
        running = False
        try:
            if client_sock:
                client_sock.close()
        except:
            pass
        try:
            srv.close()
        except:
            pass
        p.terminate()
        sys.exit(0)

    signal.signal(signal.SIGINT, sigint_handler)

    while running:
        try:
            conn, addr = srv.accept()
            client_sock = conn
            handle_client(conn, addr)
            client_sock = None
        except KeyboardInterrupt:
            sigint_handler(None, None)
        except Exception as e:
            print("Server accept error:", e)
            time.sleep(1)

if __name__ == "__main__":
    main()
