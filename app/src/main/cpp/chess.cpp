#include "chess.hpp"
#include <algorithm>
#include <cctype>
#include <cmath>
#include <sstream>
namespace chess{
static const int V[7]={0,100,320,330,500,900,20000};
static int T(int p){return p<0?-p:p;} static int C(int p){return p>0?0:1;}
static char P(int p){static const char*w=" PNBRQK";if(!p)return' ';char c=w[T(p)];return p>0?c:char(std::tolower(c));}
int sqFrom(const std::string&s){if(s.size()!=2)return-1;int f=s[0]-'a',r=s[1]-'1';return f>=0&&f<8&&r>=0&&r<8?r*8+f:-1;}
std::string sqName(int s){return std::string()+char('a'+s%8)+char('1'+s/8);}
Game::Game(){reset();}
Game::Game(const std::string&f){reset();if(f.empty())return;std::istringstream ss(f);std::string bd,sd,ca,ep;ss>>bd>>sd>>ca>>ep;std::fill(b_,b_+64,0);int r=7,x=0;for(char c:bd){if(c=='/'){--r;x=0;}else if(std::isdigit(c))x+=c-'0';else{std::string q="PNBRQKpnbrqk";int k=q.find(c);if(k<12)b_[r*8+x]=(k<6?k+1:-(k-5));++x;}}side_=sd=="b";cwk_=ca.find('K')!=std::string::npos;cwq_=ca.find('Q')!=std::string::npos;cbk_=ca.find('k')!=std::string::npos;cbq_=ca.find('q')!=std::string::npos;ep_=ep=="-"?-1:sqFrom(ep);ss>>half_>>full_;}
void Game::reset(){std::fill(b_,b_+64,0);int q[8]={4,2,3,5,6,3,2,4};for(int f=0;f<8;f++){b_[f]=q[f];b_[8+f]=1;b_[48+f]=-1;b_[56+f]=-q[f];}side_=0;ep_=-1;half_=0;full_=1;cwk_=cwq_=cbk_=cbq_=1;sans_.clear();}
bool Game::attacked(int s,int by)const{
 int r=s/8,f=s%8;int nd[8][2]={{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}};
 for(auto d:nd){int x=f+d[0],y=r+d[1];if(x>=0&&x<8&&y>=0&&y<8&&b_[y*8+x]==(by? -2:2))return true;}
 int pr=by? -1:1, py=r+(by?-1:1);for(int df:{-1,1}){int x=f+df;if(x>=0&&x<8&&py>=0&&py<8&&b_[py*8+x]==pr)return true;}
 int ds[8][2]={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
 for(int i=0;i<8;i++){int x=f+ds[i][0],y=r+ds[i][1];while(x>=0&&x<8&&y>=0&&y<8){int p=b_[y*8+x];if(p){if(C(p)==by&&((i<4&&(T(p)==4||T(p)==5))||(i>=4&&(T(p)==3||T(p)==5))))return true;break;}x+=ds[i][0];y+=ds[i][1];}}
 for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx||dy){int x=f+dx,y=r+dy;if(x>=0&&x<8&&y>=0&&y<8&&b_[y*8+x]==(by?-6:6))return true;}
 return false;
}
bool Game::inCheck(int s)const{for(int i=0;i<64;i++)if(b_[i]==(s?-6:6))return attacked(i,s^1);return true;}
void Game::pseudo(std::vector<Move>&o,bool capturesOnly)const{
 for(int s=0;s<64;s++){int p=b_[s];if(!p||C(p)!=side_)continue;int t=T(p),r=s/8,f=s%8;
  if(t==1){int d=side_?-1:1,nr=r+d;if(nr>=0&&nr<8){int q=nr*8+f;if(!capturesOnly&&!b_[q]){if(nr==0||nr==7)for(int z:{5,4,3,2})o.push_back({s,q,z});else o.push_back({s,q,0});if((side_&&r==6)||(!side_&&r==1)){int q2=q+d*8;if(!b_[q2])o.push_back({s,q2,0});}}for(int df:{-1,1}){int x=f+df;if(x>=0&&x<8){int q2=nr*8+x;if(b_[q2]&&C(b_[q2])!=side_){if(nr==0||nr==7)for(int z:{5,4,3,2})o.push_back({s,q2,z});else o.push_back({s,q2,0});}else if(q2==ep_)o.push_back({s,q2,0,false,true});}}}}
  else if(t==2){int d[8][2]={{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}};for(auto z:d){int x=f+z[0],y=r+z[1];if(x>=0&&x<8&&y>=0&&y<8){int q=y*8+x;if(!b_[q]||C(b_[q])!=side_)if(!capturesOnly||b_[q])o.push_back({s,q});}}}
  else if(t==3||t==4||t==5){int d[8][2]={{1,1},{1,-1},{-1,1},{-1,-1},{1,0},{-1,0},{0,1},{0,-1}};int a=t==3?0:t==4?4:0,z=t==3?4:8;for(int i=a;i<z;i++){int x=f+d[i][0],y=r+d[i][1];while(x>=0&&x<8&&y>=0&&y<8){int q=y*8+x;if(!b_[q]){if(!capturesOnly)o.push_back({s,q});}else{if(C(b_[q])!=side_)o.push_back({s,q});break;}if(t==5)break;x+=d[i][0];y+=d[i][1];}}}
  else {for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx||dy){int x=f+dx,y=r+dy;if(x>=0&&x<8&&y>=0&&y<8){int q=y*8+x;if(!b_[q]||C(b_[q])!=side_)o.push_back({s,q});}}if(!capturesOnly&&!inCheck(side_)){if((!side_&&cwk_&&b_[5]==0&&b_[6]==0&&!attacked(5,1)&&!attacked(6,1))||(side_&&cbk_&&b_[61]==0&&b_[62]==0&&!attacked(61,0)&&!attacked(62,0)))o.push_back({s,s+2,0,true});if((!side_&&cwq_&&b_[1]==0&&b_[2]==0&&b_[3]==0&&!attacked(3,1)&&!attacked(2,1))||(side_&&cbq_&&b_[57]==0&&b_[58]==0&&b_[59]==0&&!attacked(59,0)&&!attacked(58,0)))o.push_back({s,s-2,0,true});}}
 }}
void Game::apply(const Move&m){int p=b_[m.from],t=T(p),cap=b_[m.to];b_[m.to]=p;b_[m.from]=0;if(m.ep)b_[m.to+(side_?8:-8)]=0;if(m.promo)b_[m.to]=side_?-m.promo:m.promo;if(t==6){if(side_)cbk_=cbq_=0;else cwk_=cwq_=0;if(m.castle){if(m.to>m.from){b_[m.to-1]=b_[m.to+1];b_[m.to+1]=0;}else{b_[m.to+1]=b_[m.to-2];b_[m.to-2]=0;}}}if(t==4){if(m.from==0)cwq_=0;if(m.from==7)cwk_=0;if(m.from==56)cbq_=0;if(m.from==63)cbk_=0;}if(cap==4||cap==-4){if(m.to==0)cwq_=0;if(m.to==7)cwk_=0;if(m.to==56)cbq_=0;if(m.to==63)cbk_=0;}ep_=-1;if(t==1&&std::abs(m.to-m.from)==16)ep_=(m.to+m.from)/2;half_=(t==1||cap||m.ep)?0:half_+1;if(side_)++full_;side_^=1;}
bool Game::legalMove(const Move&m)const{Game g=*this;g.apply(m);return !g.inCheck(side_);}
std::vector<Move> Game::legal()const{std::vector<Move>a,r;pseudo(a);for(auto&m:a)if(legalMove(m))r.push_back(m);return r;}
std::vector<Move> Game::legalFrom(int s)const{std::vector<Move>r;for(auto&m:legal())if(m.from==s)r.push_back(m);return r;}
std::string Game::sanFor(const Move&m)const{std::string s;if(m.castle)return m.to>m.from?"O-O":"O-O-O";int t=T(b_[m.from]);if(t!=1)s+=P(b_[m.from]);bool cap=b_[m.to]||m.ep;if(cap&&t==1)s+=char('a'+m.from%8);if(cap)s+='x';s+=sqName(m.to);if(m.promo)s+='='+P(side_?-m.promo:m.promo);Game g=*this;g.apply(m);if(g.inCheck(g.side_))s+=g.legal().empty()?'#':'+';return s;}
bool Game::make(int from,int to,int promo){for(auto&m:legal())if(m.from==from&&m.to==to&&(!promo||m.promo==promo)){sans_.push_back(sanFor(m));apply(m);return true;}return false;}
bool Game::makeUci(const std::string&u){if(u.size()<4)return false;int p=0;if(u.size()>4){char c=std::tolower(u[4]);p=c=='q'?5:c=='r'?4:c=='b'?3:2;}return make(sqFrom(u.substr(0,2)),sqFrom(u.substr(2,2)),p);}
std::string Game::fen()const{std::string s;for(int r=7;r>=0;r--){int n=0;for(int f=0;f<8;f++){int p=b_[r*8+f];if(!p)++n;else{if(n){s+=char('0'+n);n=0;}s+=P(p);}}if(n)s+=char('0'+n);if(r)s+='/';}s+=' ';s+=side_?'b':'w';s+=' ';std::string c;if(cwk_)c+='K';if(cwq_)c+='Q';if(cbk_)c+='k';if(cbq_)c+='q';s+=c.empty()?"-":c;s+=' ';s+=ep_<0?"-":sqName(ep_);s+=" "+std::to_string(half_)+" "+std::to_string(full_);return s;}
std::string Game::pgn()const{std::string s;for(size_t i=0;i<sans_.size();i++){if(!(i&1))s+=std::to_string(i/2+1)+". ";s+=sans_[i]+" ";}return s;}
std::string Game::lastSan()const{return sans_.empty()?"":sans_.back();}
bool Game::checkmate()const{return inCheck(side_)&&legal().empty();} bool Game::stalemate()const{return !inCheck(side_)&&legal().empty();}
bool Game::draw()const{if(half_>=100)return true;int n=0;for(int i=0;i<64;i++)if(b_[i]){int t=T(b_[i]);if(t==1||t>=4)return false;if(t==2||t==3)++n;}return n<=1;}
bool Game::gameOver()const{return checkmate()||stalemate()||draw();} int Game::winner()const{return checkmate()?side_^1:-1;}
int Game::evaluate()const{int s=0;for(int i=0;i<64;i++)if(b_[i])s+=C(b_[i])?-V[T(b_[i])]:V[T(b_[i])];return s;}
int Game::minimax(int d,int a,int z)const{if(d==0||gameOver())return side_? -evaluate():evaluate();int best=-1000000;for(auto&m:legal()){Game g=*this;g.apply(m);int v=-g.minimax(d-1,-z,-a);best=std::max(best,v);a=std::max(a,v);if(a>=z)break;}return best;}
Move Game::bestMove(int d)const{auto ms=legal();Move best;if(ms.empty())return best;int sc=-1000000;for(auto&m:ms){Game g=*this;g.apply(m);int v=-g.minimax(std::max(0,d-1),-1000000,1000000);if(v>sc){sc=v;best=m;}}return best;}
}