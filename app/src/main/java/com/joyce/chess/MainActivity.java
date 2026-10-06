package com.joyce.chess;

import android.app.*;import android.os.*;import android.graphics.*;import android.view.*;import android.widget.*;import android.content.*;import android.text.InputType;
import java.util.*;import java.util.concurrent.*;import okhttp3.*;import org.json.*;

public class MainActivity extends Activity{
 static{System.loadLibrary("joyce_chess");}
 static native void nativeReset();static native void nativeSetFen(String f);static native String nativeFen();static native String nativePgn();static native int nativePiece(int s);static native int nativeTurn();static native boolean nativeMove(int a,int b,int p);static native int[] nativeLegalFrom(int s);static native String nativeLastSan();static native int nativeGameState();static native boolean nativeAiMove(int d);
 BoardView board;TextView status;TextView room;final Handler h=new Handler(Looper.getMainLooper());final OkHttpClient http=new OkHttpClient();WebSocket ws;String base="",roomId="",userId="android_"+UUID.randomUUID().toString().substring(0,8);String name="Player";
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);nativeReset();ui();}
 Button B(String s){Button b=new Button(this);b.setText(s);b.setTextSize(11);b.setTextColor(Color.WHITE);b.setBackgroundColor(Color.rgb(35,38,48));return b;}
 void ui(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(9,10,15));
  LinearLayout top=new LinearLayout(this);top.setPadding(8,4,8,4);status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(14);top.addView(status,new LinearLayout.LayoutParams(0,52,1));
  Button n=B("NEW");n.setOnClickListener(v->{nativeReset();board.invalidate();state();});top.addView(n);Button ai=B("AI");ai.setOnClickListener(v->{new Thread(()->{boolean ok=nativeAiMove(3);runOnUiThread(()->{if(ok){board.invalidate();state();}else toast("No legal move");});}).start();});top.addView(ai);Button online=B("ONLINE");online.setOnClickListener(v->dialog());top.addView(online);root.addView(top);
  board=new BoardView(this);root.addView(board,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout bot=new LinearLayout(this);bot.setPadding(8,3,8,8);room=new TextView(this);room.setTextColor(Color.LTGRAY);room.setGravity(Gravity.CENTER_VERTICAL);bot.addView(room,new LinearLayout.LayoutParams(0,48,1));Button p=B("COPY PGN");p.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PGN",nativePgn()));toast("PGN copied");});bot.addView(p);root.addView(bot);setContentView(root);state();}
 void state(){int s=nativeGameState();String t=s==1?"CHECKMATE • WHITE WINS":s==2?"CHECKMATE • BLACK WINS":s==3?"DRAW":nativeTurn()==0?"WHITE TO MOVE":"BLACK TO MOVE";status.setText("JOYCE CHESS  •  "+t+(nativeLastSan().isEmpty()?"":"  •  "+nativeLastSan()));room.setText(roomId.isEmpty()?"LOCAL GAME":"ROOM "+roomId);}
 void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
 void dialog(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(22,0,22,0);EditText u=new EditText(this);u.setHint("Server URL, e.g. http://192.168.1.10:3000");l.addView(u);EditText r=new EditText(this);r.setHint("Room code (blank creates)");r.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);l.addView(r);EditText nm=new EditText(this);nm.setHint("Player name");l.addView(nm);new AlertDialog.Builder(this).setTitle("ONLINE CHESS").setView(l).setPositiveButton("CONNECT",(d,w)->{base=u.getText().toString().trim();roomId=r.getText().toString().trim().toUpperCase();name=nm.getText().toString().trim();if(name.isEmpty())name="Android Player";connect();}).setNegativeButton("CANCEL",null).show();}
 void connect(){if(base.endsWith("/"))base=base.substring(0,base.length()-1);new Thread(()->{try{if(roomId.isEmpty()){JSONObject q=new JSONObject();q.put("hostId",userId);q.put("hostName",name);q.put("hostElo",1200);q.put("timeControl",300);q.put("isAiGame",false);q.put("preferredColor","random");JSONObject z=post(base+"/api/rooms/create",q);roomId=z.getJSONObject("room").getString("roomId");}else{JSONObject q=new JSONObject();q.put("roomId",roomId);q.put("userId",userId);q.put("userName",name);q.put("elo",1200);post(base+"/api/rooms/join",q);}openWs();runOnUiThread(this::state);}catch(Exception e){runOnUiThread(()->toast("CONNECT FAILED: "+e.getMessage()));}}).start();}
 JSONObject post(String url,JSONObject body)throws Exception{RequestBody rb=RequestBody.create(body.toString(),MediaType.get("application/json"));Response r=http.newCall(new Request.Builder().url(url).post(rb).build()).execute();if(!r.isSuccessful())throw new Exception("HTTP "+r.code());return new JSONObject(r.body().string());}
 void openWs(){if(ws!=null)ws.close(1000,null);String x=base.replaceFirst("^http","ws")+"/ws";Request req=new Request.Builder().url(x).build();ws=http.newWebSocket(req,new WebSocketListener(){
  public void onOpen(WebSocket s,Response r){ws=s;try{JSONObject p=new JSONObject();p.put("roomId",roomId);p.put("userId",userId);p.put("userName",name);p.put("elo",1200);s.send(new JSONObject().put("type","JOIN_ROOM").put("payload",p).toString());}catch(Exception ignored){}}
  public void onMessage(WebSocket s,String text){try{JSONObject z=new JSONObject(text);if("ROOM_UPDATE".equals(z.optString("type"))){JSONObject rr=z.getJSONObject("room");String f=rr.getString("fen");if(!f.equals(nativeFen()))nativeSetFen(f);runOnUiThread(()->{board.invalidate();state();});}}catch(Exception ignored){}}
  public void onFailure(WebSocket s,Throwable t,Response r){runOnUiThread(()->toast("ONLINE DISCONNECTED"));h.postDelayed(()->{if(!roomId.isEmpty())openWs();},2000);}
 });}
 void sendMove(int a,int b,int p){if(ws==null)return;try{JSONObject q=new JSONObject();q.put("roomId",roomId);q.put("from",sq(a));q.put("to",sq(b));q.put("promotion",p==5?"q":p==4?"r":p==3?"b":"n");ws.send(new JSONObject().put("type","MAKE_MOVE").put("payload",q).toString());}catch(Exception ignored){}}
 String sq(int s){return ""+(char)('a'+s%8)+(char)('1'+s/8);}
 @Override protected void onDestroy(){if(ws!=null)ws.close(1000,"bye");super.onDestroy();}

 class BoardView extends View{
  Paint p=new Paint(3);int selected=-1;int[] moves=new int[0];float z,ox,oy;String[] glyph={" ","♟","♞","♝","♜","♛","♚"};
  BoardView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  protected void onDraw(Canvas c){z=Math.min(getWidth(),getHeight())*.97f/8f;ox=(getWidth()-8*z)/2;oy=(getHeight()-8*z)/2;p.setStyle(Paint.Style.FILL);
   for(int r=0;r<8;r++)for(int f=0;f<8;f++){p.setColor(((r+f)&1)==0?Color.rgb(235,215,180):Color.rgb(116,82,53));c.drawRect(ox+f*z,oy+(7-r)*z,ox+(f+1)*z,oy+(8-r)*z,p);}
   if(selected>=0){p.setColor(Color.argb(120,212,175,55));c.drawRect(ox+selected%8*z,oy+(7-selected/8)*z,ox+(selected%8+1)*z,oy+(8-selected/8)*z,p);for(int q:moves){p.setColor(Color.argb(150,30,170,80));c.drawCircle(ox+(q%8+.5f)*z,oy+(7-q/8+.5f)*z,z*.11f,p);}}
   p.setTextAlign(Paint.Align.CENTER);p.setTextSize(z*.76f);p.setTypeface(Typeface.create("serif",Typeface.NORMAL));for(int s=0;s<64;s++){int v=nativePiece(s);if(v==0)continue;float x=ox+(s%8+.5f)*z,y=oy+(7-s/8+.73f)*z;p.setShadowLayer(4,2,2,Color.BLACK);p.setColor(v>0?Color.rgb(248,246,235):Color.rgb(25,23,23));c.drawText(glyph[Math.abs(v)],x,y,p);p.clearShadowLayer();}
   p.setTextSize(10);p.setColor(Color.argb(180,20,20,20));for(int f=0;f<8;f++)c.drawText(""+(char)('a'+f),ox+(f+.12f)*z,oy+8*z-3,p);for(int r=0;r<8;r++)c.drawText(""+(r+1),ox+3,oy+(7-r+.2f)*z,p);
  }
  public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;int f=(int)((e.getX()-ox)/z),r=7-(int)((e.getY()-oy)/z);if(f<0||f>7||r<0||r>7)return true;int s=r*8+f;
   if(selected<0){int pc=nativePiece(s);if(pc!=0&&((pc>0)==(nativeTurn()==0))&&nativeGameState()==0){selected=s;moves=nativeLegalFrom(s);invalidate();}}
   else{boolean ok=false;for(int q:moves)if(q==s)ok=true;if(ok){int p=0,pc=nativePiece(selected);if(Math.abs(pc)==1&&(s/8==0||s/8==7))p=5;if(nativeMove(selected,s,p)){sendMove(selected,s,p);selected=-1;moves=new int[0];invalidate();state();}}else{selected=-1;moves=new int[0];invalidate();}}return true;}
 }
}