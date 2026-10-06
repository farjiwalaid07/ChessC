#include "../app/src/main/cpp/chess.hpp"
#include <cassert>
#include <iostream>
using namespace chess;
int main(){
 Game g;
 assert(g.makeUci("e2e4")); assert(g.makeUci("e7e5")); assert(g.makeUci("g1f3")); assert(g.makeUci("b8c6")); assert(g.makeUci("f1b5"));
 assert(!g.makeUci("e1e3"));
 Game c("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"); assert(c.makeUci("e1g1")); assert(c.piece(5)==4);\n Game q("4k3/8/8/8/8/8/8/3QK3 w - - 0 1"); assert(q.makeUci("d1d8"));
 Game e("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"); assert(e.makeUci("e5d6")); assert(e.piece(sqFrom("d5"))==0);
 Game p("4k3/P7/8/8/8/8/8/4K3 w - - 0 1"); assert(p.makeUci("a7a8q")); assert(p.piece(sqFrom("a8"))==5);
 Game m("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1"); assert(m.checkmate()&&m.winner()==0);
 std::cout<<"CHESS_SMOKE_OK\n";
}