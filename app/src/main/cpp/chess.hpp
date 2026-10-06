#pragma once
#include <string>
#include <vector>
namespace chess {
struct Move{int from=-1,to=-1,promo=0;bool castle=false,ep=false;};
class Game{
 int b_[64]{},side_=0,ep_=-1,half_=0,full_=1; bool cwk_=1,cwq_=1,cbk_=1,cbq_=1; std::vector<std::string> sans_;
 bool attacked(int,int)const; bool inCheck(int)const; void pseudo(std::vector<Move>&,bool=false)const; bool legalMove(const Move&)const; void apply(const Move&);
 std::string sanFor(const Move&)const; int minimax(int,int,int)const;
public:
 Game(); explicit Game(const std::string&); void reset(); bool make(int,int,int=0); bool makeUci(const std::string&);
 std::vector<Move> legal()const; std::vector<Move> legalFrom(int)const; std::string fen()const; std::string pgn()const; std::string lastSan()const;
 int piece(int s)const{return b_[s];} int turn()const{return side_;} bool gameOver()const; bool checkmate()const; bool stalemate()const; bool draw()const; int winner()const; int evaluate()const; Move bestMove(int)const;
};
int sqFrom(const std::string&); std::string sqName(int);
}