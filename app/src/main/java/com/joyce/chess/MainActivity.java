package com.joyce.chess;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.*;
import android.text.InputType;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;

public class MainActivity extends Activity {
  static { System.loadLibrary("joyce_chess"); }
  static native void nativeReset();
  static native void nativeSetFen(String f);
  static native String nativeFen();
  static native String nativePgn();
  static native int nativePiece(int s);
  static native int nativeTurn();
  static native boolean nativeMove(int a,int b,int p);
  static native int[] nativeLegalFrom(int s);
  static native String nativeLastSan();
  static native int nativeGameState();
  static native String nativeStockfishBestMove(String fen,int depth);

  static final String APP="ChessCrazy";
  static final String START="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
  final Handler handler=new Handler(Looper.getMainLooper());
  final OkHttpClient client=new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
  SharedPreferences prefs;
  BoardView board;
  TextView title,sub;
  WebSocket ws;
  String server="",roomId="",playerId="cc_"+UUID.randomUUID().toString().replace("-","").substring(0,10);
  String playerName="Player",opponent="Opponent";
  int playerSide=-1,selected=-1;
  int[] selectedMoves=new int[0];
  boolean online=false,spectator=false,flipped=false,gameFinished=false;
  String remoteResult=null;
  int baseSeconds=300,increment=0,whiteMs=300000,blackMs=300000;
  long lastClock=System.currentTimeMillis();
  boolean clockRunning=false;
  ArrayList<String> moveSans=new ArrayList<>();
  Runnable clockTask;



  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    prefs=getSharedPreferences("chesscrazy",MODE_PRIVATE);\n    getWindow().setStatusBarColor(Color.rgb(8,10,16));
    getWindow().setNavigationBarColor(Color.rgb(8,10,16));
    nativeReset();
    playerName=prefs.getString("name","Player");
    buildUi();
    startClock();
  }

  Button btn(String text){
    Button b=new Button(this); b.setText(text); b.setTextSize(11); b.setAllCaps(false);
    b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.rgb(32,36,48)); return b;
  }

  void buildUi(){
    LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.rgb(8,10,16));
    LinearLayout bar=new LinearLayout(this); bar.setPadding(8,4,8,4);
    LinearLayout labels=new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
    title=new TextView(this); title.setTextColor(Color.WHITE); title.setTextSize(19); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    sub=new TextView(this); sub.setTextColor(Color.LTGRAY); sub.setTextSize(12);
    labels.addView(title); labels.addView(sub);
    bar.addView(labels,new LinearLayout.LayoutParams(0,62,1));
    Button menu=btn("☰"); menu.setTextSize(20); menu.setOnClickListener(v->showMenu()); bar.addView(menu,new LinearLayout.LayoutParams(55,62));
    root.addView(bar);
    board=new BoardView(this); root.addView(board,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout clocks=new LinearLayout(this); clocks.setPadding(12,4,12,4);
    clocks.addView(clockLabel(true),new LinearLayout.LayoutParams(0,48,1));
    clocks.addView(clockLabel(false),new LinearLayout.LayoutParams(0,48,1));
    root.addView(clocks);
    LinearLayout actions=new LinearLayout(this); actions.setPadding(8,2,8,7);
    Button draw=btn("½ Draw"); draw.setOnClickListener(v->sendSimple("drawOffer")); actions.addView(draw,new LinearLayout.LayoutParams(0,48,1));
    Button resign=btn("Resign"); resign.setOnClickListener(v->confirmResign()); actions.addView(resign,new LinearLayout.LayoutParams(0,48,1));
    Button pgn=btn("PGN"); pgn.setOnClickListener(v->showPgn()); actions.addView(pgn,new LinearLayout.LayoutParams(0,48,1));
    root.addView(actions);
    setContentView(root); refresh();
  }

  TextView clockLabel(boolean white){
    TextView t=new TextView(this); t.setTextColor(Color.WHITE); t.setTextSize(18); t.setGravity(Gravity.CENTER);
    t.setTypeface(Typeface.MONOSPACE,Typeface.BOLD); t.setText(white?"White  5:00":"Black  5:00"); t.setTag(white);
    return t;
  }

  void refresh(){
    String state=remoteResult!=null?remoteResult:"";
    if(state.isEmpty()){
      int s=nativeGameState();
      state=s==1?"CHECKMATE • WHITE WINS":s==2?"CHECKMATE • BLACK WINS":s==3?"DRAW":nativeTurn()==0?"WHITE TO MOVE":"BLACK TO MOVE";
    }
    title.setText(APP+(online?"  •  "+roomId:""));
    sub.setText((online?(playerSide==0?"You are WHITE":"You are BLACK"):"Local game")+"  •  "+state+
      (nativeLastSan().isEmpty()?"":"  •  "+nativeLastSan()));
    try{getClock(true).setText("White  "+fmt(whiteMs));getClock(false).setText("Black  "+fmt(blackMs));}catch(Exception ignored){}
  }

  TextView getClock(boolean white){
    ViewGroup root=(ViewGroup)((ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
    ViewGroup clocks=(ViewGroup)root.getChildAt(2);
    return (TextView)clocks.getChildAt(white?0:1);
  }

  String fmt(int ms){int s=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",s/60,s%60);}

  void startClock(){
    clockTask=()->{
      if(!gameFinished && clockRunning){
        long now=System.currentTimeMillis(),d=now-lastClock; lastClock=now;
        if(nativeTurn()==0) whiteMs-=d; else blackMs-=d;
        if(whiteMs<=0){whiteMs=0;finishLocal("blackWin","timeout");}
        if(blackMs<=0){blackMs=0;finishLocal("whiteWin","timeout");}
        try{getClock(true).setText("White  "+fmt(whiteMs));getClock(false).setText("Black  "+fmt(blackMs));}catch(Exception ignored){}
      }
      handler.postDelayed(clockTask,100);
    };
    handler.postDelayed(clockTask,100);
  }

  void showMenu(){
    String[] items={"New Game","Play vs Stockfish","Local 2 Player","Online Multiplayer","Profile","Match History","Achievements","Friends","Settings","About"};
    new AlertDialog.Builder(this).setTitle("ChessCrazy").setItems(items,(d,w)->{
      switch(w){
        case 0: newGameDialog(); break; case 1: startAiDialog(); break; case 2: startLocal(); break;
        case 3: onlineDialog(); break; case 4: profileDialog(); break; case 5: historyDialog(); break;
        case 6: achievementsDialog(); break; case 7: friendsDialog(); break; case 8: settingsDialog(); break; default: aboutDialog();
      }
    }).show();
  }

  void newGameDialog(){new AlertDialog.Builder(this).setTitle("New Game").setItems(new String[]{"Stockfish","Local 2 Player","Online"},(d,w)->{if(w==0)startAiDialog();else if(w==1)startLocal();else onlineDialog();}).show();}

  void startLocal(){disconnect();online=false;playerSide=-1;remoteResult=null;spectator=false;prefs.edit().putBoolean("ai",false).apply();resetGame(300,0);toast("Local 2 Player ready");}

  void startAiDialog(){
    String[] ds={"Beginner • depth 8","Club • depth 12","Strong • depth 16","Master • depth 20"};
    new AlertDialog.Builder(this).setTitle("Play vs Local Stockfish").setItems(ds,(d,w)->{
      prefs.edit().putInt("aiDepth",new int[]{8,12,16,20}[w]).apply();
      disconnect();online=false;playerSide=0;remoteResult=null;spectator=false;prefs.edit().putBoolean("ai",true).apply();resetGame(300,0);
      if(nativeTurn()!=0) aiMove(); else toast("Stockfish ready • your move");
    }).show();
  }

  void aiMove(){
    if(gameFinished)return;
    final int depth=prefs.getInt("aiDepth",12);
    sub.setText("Stockfish is thinking…");
    new Thread(()->{
      String u=nativeStockfishBestMove(nativeFen(),depth);
      runOnUiThread(()->{
        if(u!=null && u.length()>=4){
          int a=sq(u.substring(0,2)),b=sq(u.substring(2,4));int p=0;
          if(u.length()>4)p=uciPromo(u.charAt(4));
          if(nativeMove(a,b,p)){moveSans.add(nativeLastSan());whiteMs+=increment*1000;blackMs+=increment*1000;board.invalidate();refresh();checkTerminal();}
        }else toast("Stockfish unavailable");
      });
    }).start();
  }

  int uciPromo(char c){return c=='q'?5:c=='r'?4:c=='b'?3:2;}

  void resetGame(int minutes,int inc){
    nativeReset();baseSeconds=minutes;increment=inc;whiteMs=minutes*1000;blackMs=whiteMs;
    moveSans.clear();selected=-1;selectedMoves=new int[0];gameFinished=false;clockRunning=true;lastClock=System.currentTimeMillis();
    board.invalidate();refresh();
  }

  void onlineDialog(){
    LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(18,0,18,0);
    EditText url=new EditText(this);url.setHint("Cloudflare Worker URL");url.setText(prefs.getString("server",""));l.addView(url);
    EditText code=new EditText(this);code.setHint("Room code • blank creates one");code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);l.addView(code);
    EditText name=new EditText(this);name.setHint("Player name");name.setText(playerName);l.addView(name);
    String[] tc={"3 + 0","5 + 0","10 + 0","5 + 3","10 + 5"};
    Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,tc));l.addView(sp);
    new AlertDialog.Builder(this).setTitle("ONLINE • CLOUDFLARE").setView(l).setPositiveButton("CONNECT",(d,w)->{
      server=url.getText().toString().trim();roomId=code.getText().toString().trim().toUpperCase();playerName=name.getText().toString().trim();
      if(playerName.isEmpty())playerName="Player";prefs.edit().putString("server",server).putString("name",playerName).apply();
      int idx=sp.getSelectedItemPosition();baseSeconds=new int[]{180,300,600,300,600}[idx];increment=new int[]{0,0,0,3,5}[idx];connectOnline();
    }).setNegativeButton("CANCEL",null).show();
  }

  void connectOnline(){
    if(server.isEmpty()){toast("Enter your Cloudflare Worker URL");return;}
    if(server.endsWith("/"))server=server.substring(0,server.length()-1);
    if(roomId.isEmpty()){
      new Thread(()->{
        try{
          RequestBody body=RequestBody.create("{}",MediaType.get("application/json"));
          Response r=client.newCall(new Request.Builder().url(server+"/rooms").post(body).build()).execute();
          JSONObject z=new JSONObject(r.body().string());roomId=z.getString("code");openRoomSocket();
        }catch(Exception e){runOnUiThread(()->toast("Room creation failed: "+e.getMessage()));}
      }).start();
    }else openRoomSocket();
  }

  void openRoomSocket(){
    disconnect();
    online=true;remoteResult=null;playerSide=-1;spectator=false;
    String wsUrl=server.replaceFirst("^https","wss").replaceFirst("^http","ws")+"/ws/"+roomId;
    ws=client.newWebSocket(new Request.Builder().url(wsUrl).build(),new WebSocketListener(){
      @Override public void onOpen(WebSocket s,Response r){
        ws=s;
        try{
          JSONObject p=new JSONObject();p.put("username",playerName);p.put("elo",prefs.getInt("elo",1200));p.put("side","random");
          s.send(new JSONObject().put("t","hello").put("room",roomId).put("from",playerId).put("name",playerName).put("p",p).toString());
        }catch(Exception ignored){}
      }
      @Override public void onMessage(WebSocket s,String text){handleWs(text);}
      @Override public void onFailure(WebSocket s,Throwable t,Response r){runOnUiThread(()->toast("Cloudflare connection lost"));handler.postDelayed(()->{if(online&&!roomId.isEmpty())openRoomSocket();},2000);}
    });
    runOnUiThread(()->{resetGame(baseSeconds,increment);toast("Room "+roomId+" • share the code");});
  }

  void handleWs(String text){
    try{
      JSONObject z=new JSONObject(text);String t=z.optString("t");JSONObject p=z.optJSONObject("p");
      if("connected".equals(t)){if(p!=null)playerSide="w".equals(p.optString("side"))?0:"b".equals(p.optString("side"))?1:-1;runOnUiThread(this::refresh);return;}
      if("roomJoined".equals(t)){
        if(p!=null){
          playerSide="w".equals(p.optString("side"))?0:"b".equals(p.optString("side"))?-1:1;
          if(playerSide==-1 && "spectator".equals(p.optString("role")))spectator=true;
          opponent=p.optJSONObject("opponent")!=null?p.getJSONObject("opponent").optString("name","Opponent"):"Opponent";
          String f=p.optString("fen","");if(!f.isEmpty())nativeSetFen(f);
          int ply=p.optInt("ply",0);whiteMs=p.optInt("whiteMs",baseSeconds*1000);blackMs=p.optInt("blackMs",baseSeconds*1000);
        }
        runOnUiThread(()->{clockRunning=true;board.invalidate();refresh();});return;
      }
      if("roomSnapshot".equals(t)){
        if(p!=null){
          String f=p.optString("fen","");if(!f.isEmpty()&&!f.equals(nativeFen()))nativeSetFen(f);
          remoteResult=p.isNull("result")?null:p.optString("result",null);
          if(remoteResult!=null&&!remoteResult.equals("null"))gameFinished=true;
          runOnUiThread(()->{board.invalidate();refresh();});
        }return;
      }
      if("move".equals(t)){
        if(p!=null){String f=p.optString("fen","");if(!f.isEmpty()&&!f.equals(nativeFen()))nativeSetFen(f);remoteResult=p.isNull("result")?null:p.optString("result",null);}
        runOnUiThread(()->{board.invalidate();refresh();});return;
      }
      if("chat".equals(t)){runOnUiThread(()->toast(z.optString("name","Opponent")+": "+(p==null?"":p.optString("message",""))));return;}
      if("drawOffer".equals(t)){runOnUiThread(()->drawOfferDialog());return;}
      if("drawAccept".equals(t)){remoteResult="draw";gameFinished=true;runOnUiThread(this::refresh);return;}
      if("resign".equals(t)){String from=z.optString("from");remoteResult=from.equals(playerId)?"resigned":"resignation";gameFinished=true;runOnUiThread(this::refresh);return;}
      if("rematchOffer".equals(t)){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Rematch").setMessage(z.optString("name","Opponent")+" wants a rematch.").setPositiveButton("Accept",(d,w)->sendSimple("rematchAccept")).setNegativeButton("Decline",(d,w)->sendSimple("rematchDecline")).show());return;}
      if("rematchAccept".equals(t)){runOnUiThread(()->{resetGame(baseSeconds,increment);toast("Rematch started");});return;}
      if("error".equals(t)){runOnUiThread(()->toast("Server: "+(p==null?"Move rejected":p.optString("error","Error"))));if(p!=null){String f=p.optString("fen","");if(!f.isEmpty())nativeSetFen(f);board.invalidate();refresh();}return;}
    }catch(Exception ignored){}
  }

  void sendMove(int a,int b,int promo){
    if(ws==null)return;
    try{
      JSONObject p=new JSONObject();p.put("uci",sqName(a)+sqName(b)+(promo==5?"q":promo==4?"r":promo==3?"b":promo==2?"n":""));p.put("ply",moveSans.size());p.put("fen",nativeFen());p.put("whiteMs",whiteMs);p.put("blackMs",blackMs);
      ws.send(new JSONObject().put("t","move").put("room",roomId).put("from",playerId).put("name",playerName).put("seq",moveSans.size()).put("p",p).toString());
    }catch(Exception ignored){}
  }

  void sendSimple(String type){
    if(ws==null){if("drawOffer".equals(type))toast("Draw is only available online");return;}
    try{ws.send(new JSONObject().put("t",type).put("room",roomId).put("from",playerId).put("name",playerName).put("p",new JSONObject()).toString());}catch(Exception ignored){}
  }

  void disconnect(){if(ws!=null){try{ws.close(1000,"bye");}catch(Exception ignored){}ws=null;}online=false;}

  void finishLocal(String result,String reason){
    if(gameFinished)return;gameFinished=true;clockRunning=false;remoteResult=result;saveMatch(result,reason);
    if(online && ("timeout".equals(reason)||"resignation".equals(reason)))sendSimple("resign");
    refresh();
    new AlertDialog.Builder(this).setTitle(result.equals("draw")?"DRAW":result.equals("whiteWin")?"WHITE WINS":"BLACK WINS")
      .setMessage(reason.toUpperCase(Locale.US)+"\n\n"+nativePgn()).setPositiveButton("Rematch", (d,w)->{if(online)sendSimple("rematchOffer");else resetGame(baseSeconds,increment);})
      .setNeutralButton("Copy PGN",(d,w)->copyPgn()).setNegativeButton("Close",null).show();
  }

  void checkTerminal(){
    int s=nativeGameState();if(s==1)finishLocal("whiteWin","checkmate");else if(s==2)finishLocal("blackWin","checkmate");else if(s==3)finishLocal("draw","draw");
    if(!gameFinished){clockRunning=true;lastClock=System.currentTimeMillis();if(!online&&prefs.getBoolean("ai",false)&&nativeTurn()==1)aiMove();}
  }

  void saveMatch(String result,String reason){
    try{
      JSONArray h=new JSONArray(prefs.getString("history","[]"));JSONObject g=new JSONObject();
      g.put("date",System.currentTimeMillis());g.put("result",result);g.put("reason",reason);g.put("pgn",nativePgn());g.put("moves",moveSans.size());g.put("opponent",online?opponent:"Stockfish");
      JSONArray n=new JSONArray();n.put(g);for(int i=0;i<Math.min(49,h.length());i++)n.put(h.get(i));prefs.edit().putString("history",n.toString()).apply();
      int elo=prefs.getInt("elo",1200),wins=prefs.getInt("wins",0),loss=prefs.getInt("losses",0),draws=prefs.getInt("draws",0);
      if(("whiteWin".equals(result)&&playerSide==0)||("blackWin".equals(result)&&playerSide==1)||(!online&&"whiteWin".equals(result))) {elo+=Math.max(5,32);wins++;} else if("draw".equals(result)){draws++;} else {elo-=Math.min(32,Math.max(5,32));losses++;}
      prefs.edit().putInt("elo",Math.max(100,elo)).putInt("wins",wins).putInt("losses",losses).putInt("draws",draws).apply();
    }catch(Exception ignored){}
  }

  void copyPgn(){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PGN",nativePgn()));toast("PGN copied");}
  void showPgn(){new AlertDialog.Builder(this).setTitle("Game PGN").setMessage(nativePgn().isEmpty()?"No moves yet":nativePgn()).setPositiveButton("Copy",(d,w)->copyPgn()).setNegativeButton("Close",null).show();}

  void confirmResign(){if(gameFinished)return;new AlertDialog.Builder(this).setTitle("Resign?").setMessage("Give up this game?").setPositiveButton("Resign",(d,w)->finishLocal(nativeTurn()==0?"blackWin":"whiteWin","resignation")).setNegativeButton("Cancel",null).show();}
  void drawOfferDialog(){new AlertDialog.Builder(this).setTitle("Draw offer").setMessage("Opponent offers a draw.").setPositiveButton("Accept",(d,w)->{sendSimple("drawAccept");remoteResult="draw";gameFinished=true;refresh();}).setNegativeButton("Decline",(d,w)->sendSimple("drawDecline")).show();}

  void profileDialog(){
    LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(18,0,18,0);
    EditText n=new EditText(this);n.setHint("Username");n.setText(playerName);l.addView(n);
    int elo=prefs.getInt("elo",1200),wins=prefs.getInt("wins",0),loss=prefs.getInt("losses",0),draws=prefs.getInt("draws",0),level=prefs.getInt("level",1),xp=prefs.getInt("xp",0);
    TextView stats=new TextView(this);stats.setTextColor(Color.WHITE);stats.setPadding(4,12,4,4);stats.setText("ELO "+elo+"\nLevel "+level+" • "+xp+" XP\nW "+wins+"  L "+losses+"  D "+draws);l.addView(stats);
    new AlertDialog.Builder(this).setTitle("PROFILE").setView(l).setPositiveButton("Save",(d,w)->{playerName=n.getText().toString().trim();if(playerName.isEmpty())playerName="Player";prefs.edit().putString("name",playerName).apply();refresh();}).setNegativeButton("Close",null).show();
  }

  void historyDialog(){
    try{
      JSONArray h=new JSONArray(prefs.getString("history","[]"));StringBuilder s=new StringBuilder();
      for(int i=0;i<h.length();i++){JSONObject g=h.getJSONObject(i);s.append(i+1).append(". ").append(g.optString("result")).append(" • ").append(g.optString("opponent")).append(" • ").append(g.optInt("moves")).append(" moves\n");}
      new AlertDialog.Builder(this).setTitle("MATCH HISTORY • LAST 50").setMessage(s.length()==0?"No games recorded yet.":s.toString()).setPositiveButton("Close",null).show();
    }catch(Exception e){toast("History unavailable");}
  }

  void achievementsDialog(){
    int wins=prefs.getInt("wins",0),level=prefs.getInt("level",1);
    String s="⚔ First Blood — win 1 match: "+Math.min(wins,1)+"/1\n🔥 On Fire — 3 wins: "+Math.min(wins,3)+"/3\n⚡ Unstoppable — 5 wins: "+Math.min(wins,5)+"/5\n♟ Chess Apprentice — 10 wins: "+Math.min(wins,10)+"/10\n👑 Grand Tactician — 50 wins: "+Math.min(wins,50)+"/50\n⭐ Rising Star — Level 5: "+Math.min(level,5)+"/5\n🌟 Master Mind — Level 10: "+Math.min(level,10)+"/10";
    new AlertDialog.Builder(this).setTitle("ACHIEVEMENTS").setMessage(s).setPositiveButton("Close",null).show();
  }

  void friendsDialog(){
    final EditText e=new EditText(this);e.setHint("Friend username");e.setSingleLine();
    new AlertDialog.Builder(this).setTitle("FRIENDS").setMessage("Friends are stored locally on this device. Add an opponent after a match or enter a username below.")
      .setView(e).setPositiveButton("Add Friend",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty()){JSONArray a;try{a=new JSONArray(prefs.getString("friends","[]"));a.put(n);prefs.edit().putString("friends",a.toString()).apply();toast(n+" added");}catch(Exception ignored){}}})
      .setNegativeButton("Close",null).show();
  }

  void settingsDialog(){
    String[] items={"Board: Classic","Board: Green","Board: Blue","Flip board","Sound effects","Show coordinates","Clear local history"};
    boolean[] checked={prefs.getInt("theme",0)==0,prefs.getInt("theme",0)==1,prefs.getInt("theme",0)==2,prefs.getBoolean("flip",false),prefs.getBoolean("sound",true),prefs.getBoolean("coords",true),false};
    new AlertDialog.Builder(this).setTitle("SETTINGS").setMultiChoiceItems(items,checked,(d,w,on)->{
      if(w<=2&&on){prefs.edit().putInt("theme",w).apply();board.invalidate();}
      else if(w==3)prefs.edit().putBoolean("flip",on).apply();
      else if(w==4)prefs.edit().putBoolean("sound",on).apply();
      else if(w==5)prefs.edit().putBoolean("coords",on).apply();
      else if(w==6&&on)prefs.edit().remove("history").apply();
    }).setPositiveButton("Done",(d,w)->board.invalidate()).show();
  }

  void aboutDialog(){new AlertDialog.Builder(this).setTitle("ChessCrazy").setMessage("Native C++17 Android chess\nLocal Stockfish engine\nCloudflare Durable Object multiplayer\nOffline profile, history, achievements and settings\n\nNo Gemini or cloud AI.").setPositiveButton("Close",null).show();}

  void promotion(int from,int to){
    String[] choices={"Queen","Rook","Bishop","Knight"};new AlertDialog.Builder(this).setTitle("Promote pawn").setItems(choices,(d,w)->{
      int p=new int[]{5,4,3,2}[w];if(nativeMove(from,to,p)){moveSans.add(nativeLastSan());sendMove(from,to,p);board.invalidate();checkTerminal();}
    }).show();
  }

  int sq(String s){return (s.charAt(1)-'1')*8+(s.charAt(0)-'a');}
  String sqName(int s){return ""+(char)('a'+s%8)+(char)('1'+s/8);}

  class BoardView extends View{
    Paint p=new Paint(3);float z,ox,oy;String[] glyph={" ","♙","♘","♗","♖","♕","♔"};
    BoardView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    protected void onDraw(Canvas c){
      z=Math.min(getWidth(),getHeight())*.97f/8f;ox=(getWidth()-8*z)/2;oy=(getHeight()-8*z)/2;
      int theme=prefs.getInt("theme",0);int light=theme==1?Color.rgb(190,215,170):theme==2?Color.rgb(185,205,225):Color.rgb(235,215,180);int dark=theme==1?Color.rgb(80,120,72):theme==2?Color.rgb(62,92,125):Color.rgb(116,82,53);
      flipped=prefs.getBoolean("flip",false);
      for(int r=0;r<8;r++)for(int f=0;f<8;f++){p.setColor(((r+f)&1)==0?light:dark);float x=ox+f*z,y=oy+(7-r)*z;c.drawRect(x,y,x+z,y+z,p);}
      if(selected>=0){p.setColor(Color.argb(120,255,196,55));drawSq(c,selected);for(int q:selectedMoves){p.setColor(Color.argb(170,40,200,100));c.drawCircle(ox+(q%8+.5f)*z,oy+(7-q/8+.5f)*z,z*.11f,p);}}
      p.setTextAlign(Paint.Align.CENTER);p.setTextSize(z*.76f);p.setTypeface(Typeface.create("serif",Typeface.NORMAL));
      for(int s=0;s<64;s++){int v=nativePiece(s);if(v==0)continue;int d=displaySquare(s);float x=ox+(d%8+.5f)*z,y=oy+(7-d/8+.73f)*z;p.setShadowLayer(4,2,2,Color.BLACK);p.setColor(v>0?Color.rgb(248,246,235):Color.rgb(25,23,23));c.drawText(v>0?new String[]{" ","♙","♘","♗","♖","♕","♔"}[Math.abs(v)]:new String[]{" ","♟","♞","♝","♜","♛","♚"}[Math.abs(v)],x,y,p);p.clearShadowLayer();}
      if(prefs.getBoolean("coords",true)){p.setTextSize(10);p.setColor(Color.argb(180,30,30,30));for(int f=0;f<8;f++)c.drawText(""+(char)('a'+f),ox+(f+.12f)*z,oy+8*z-3,p);for(int r=0;r<8;r++)c.drawText(""+(r+1),ox+3,oy+(7-r+.2f)*z,p);}
    }
    void drawSq(Canvas c,int s){int d=displaySquare(s);c.drawRect(ox+d%8*z,oy+(7-d/8)*z,ox+(d%8+1)*z,oy+(8-d/8)*z,p);}
    int displaySquare(int s){return flipped?((7-s%8)+(7-s/8)*8):s;}
    public boolean onTouchEvent(MotionEvent e){
      if(e.getAction()!=MotionEvent.ACTION_UP)return true;
      int f=(int)((e.getX()-ox)/z),r=7-(int)((e.getY()-oy)/z);if(flipped){f=7-f;r=7-r;}if(f<0||f>7||r<0||r>7)return true;int s=r*8+f;
      if(gameFinished||spectator)return true;
      if(online && (playerSide<0 || playerSide!=nativeTurn()))return true;
      if(!online && prefs.getBoolean("ai",false) && nativeTurn()!=0)return true;
      if(selected<0){int pc=nativePiece(s);if(pc!=0&&((pc>0)==(nativeTurn()==0))){selected=s;selectedMoves=nativeLegalFrom(s);invalidate();}return true;}
      boolean ok=false;for(int q:selectedMoves)if(q==s)ok=true;
      if(!ok){selected=-1;selectedMoves=new int[0];invalidate();return true;}
      int pc=nativePiece(selected);if(Math.abs(pc)==1&&(s/8==0||s/8==7)){promotion(selected,s);selected=-1;selectedMoves=new int[0];invalidate();return true;}
      if(nativeMove(selected,s,0)){moveSans.add(nativeLastSan());sendMove(selected,s,0);selected=-1;selectedMoves=new int[0];invalidate();checkTerminal();}return true;
    }
  }

  @Override protected void onDestroy(){disconnect();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
