#include <jni.h>
#include "chess.hpp"
#include <mutex>
using namespace chess;
static Game g; static std::mutex m;
extern "C" JNIEXPORT void JNICALL Java_com_joyce_chess_MainActivity_nativeReset(JNIEnv*,jclass){std::lock_guard<std::mutex>l(m);g.reset();}
extern "C" JNIEXPORT void JNICALL Java_com_joyce_chess_MainActivity_nativeSetFen(JNIEnv*e,jclass,jstring s){const char*p=e->GetStringUTFChars(s,0);{std::lock_guard<std::mutex>l(m);g=Game(p);}e->ReleaseStringUTFChars(s,p);}
extern "C" JNIEXPORT jstring JNICALL Java_com_joyce_chess_MainActivity_nativeFen(JNIEnv*e,jclass){std::lock_guard<std::mutex>l(m);return e->NewStringUTF(g.fen().c_str());}
extern "C" JNIEXPORT jstring JNICALL Java_com_joyce_chess_MainActivity_nativePgn(JNIEnv*e,jclass){std::lock_guard<std::mutex>l(m);return e->NewStringUTF(g.pgn().c_str());}
extern "C" JNIEXPORT jint JNICALL Java_com_joyce_chess_MainActivity_nativePiece(JNIEnv*,jclass,jint s){std::lock_guard<std::mutex>l(m);return g.piece(s);}
extern "C" JNIEXPORT jint JNICALL Java_com_joyce_chess_MainActivity_nativeTurn(JNIEnv*,jclass){std::lock_guard<std::mutex>l(m);return g.turn();}
extern "C" JNIEXPORT jboolean JNICALL Java_com_joyce_chess_MainActivity_nativeMove(JNIEnv*,jclass,jint a,jint b,jint p){std::lock_guard<std::mutex>l(m);return g.make(a,b,p);}
extern "C" JNIEXPORT jintArray JNICALL Java_com_joyce_chess_MainActivity_nativeLegalFrom(JNIEnv*e,jclass,jint s){std::lock_guard<std::mutex>l(m);auto v=g.legalFrom(s);jintArray a=e->NewIntArray((jsize)v.size());for(size_t i=0;i<v.size();++i)e->SetIntArrayRegion(a,i,1,(jint*)&v[i].to);return a;}
extern "C" JNIEXPORT jstring JNICALL Java_com_joyce_chess_MainActivity_nativeLastSan(JNIEnv*e,jclass){std::lock_guard<std::mutex>l(m);return e->NewStringUTF(g.lastSan().c_str());}
extern "C" JNIEXPORT jint JNICALL Java_com_joyce_chess_MainActivity_nativeGameState(JNIEnv*,jclass){std::lock_guard<std::mutex>l(m);if(g.checkmate())return g.winner()==0?1:2;if(g.draw()||g.stalemate())return 3;return 0;}
extern "C" JNIEXPORT jboolean JNICALL Java_com_joyce_chess_MainActivity_nativeAiMove(JNIEnv*,jclass,jint d){std::lock_guard<std::mutex>l(m);auto x=g.bestMove(d);return x.from>=0&&g.make(x.from,x.to,x.promo);}
