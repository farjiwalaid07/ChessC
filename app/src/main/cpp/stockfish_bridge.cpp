#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <string>
#include <thread>
#include <chrono>
#include <algorithm>
extern "C" int stockfish_init();
extern "C" int stockfish_main();
extern "C" ssize_t stockfish_stdin_write(char *data);
extern "C" char *stockfish_stdout_read();
static std::mutex sfMutex; static bool sfStarted=false; static std::thread sfThread; static bool handshake=false;
static void startLocked(){if(sfStarted)return;if(stockfish_init()!=0)return;sfThread=std::thread([](){stockfish_main();});sfStarted=true;}
static void sendLocked(const std::string& c){std::string s=c+"\n";stockfish_stdin_write(s.data());}
static std::string readUntilLocked(const std::string& token,int maxMs){std::string out;for(int i=0;i<maxMs/5;i++){char*p=stockfish_stdout_read();if(p){out+=p;if(out.find(token)!=std::string::npos)break;}else std::this_thread::sleep_for(std::chrono::milliseconds(5));}return out;}
extern "C" JNIEXPORT jstring JNICALL Java_com_joyce_chess_MainActivity_nativeStockfishBestMove(JNIEnv*env,jclass,jstring jfen,jint depth){
 const char*fen=env->GetStringUTFChars(jfen,nullptr);std::lock_guard<std::mutex>lock(sfMutex);startLocked();if(!sfStarted){env->ReleaseStringUTFChars(jfen,fen);return env->NewStringUTF("");}
 if(!handshake){sendLocked("uci");readUntilLocked("uciok",15000);sendLocked("isready");readUntilLocked("readyok",15000);handshake=true;}
 sendLocked(std::string("position fen ")+fen);sendLocked(std::string("go depth ")+std::to_string(std::max(6,(int)depth)));std::string out=readUntilLocked("bestmove ",30000);env->ReleaseStringUTFChars(jfen,fen);
 size_t p=out.rfind("bestmove ");if(p==std::string::npos)return env->NewStringUTF("");p+=9;size_t e=out.find_first_of(" \r\n",p);std::string best=out.substr(p,e==std::string::npos?std::string::npos:e-p);return env->NewStringUTF(best.c_str());
}