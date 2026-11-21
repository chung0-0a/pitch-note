package com.pitchnote.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  final int NAVY=Color.rgb(25,39,65), BLUE=Color.rgb(72,123,230), MUTED=Color.rgb(126,143,166);
  final String[] NOTES={"도","도♯","레","레♯","미","파","파♯","솔","솔♯","라","라♯","시"};
  final String[] EN={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
  LinearLayout root; TextView target, current, difference, status, micButton; PitchGraph graph; Keyboard keyboard;
  volatile int targetMidi=60; volatile boolean recording=false; volatile AudioRecord recorder;
  final ArrayDeque<float[]> samples=new ArrayDeque<>(); long graphStart;
  final ExecutorService audioPool=Executors.newCachedThreadPool(); final ConcurrentHashMap<Integer,Tone> tones=new ConcurrentHashMap<>();
  int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);} float hz(int midi){return (float)(440*Math.pow(2,(midi-69)/12.0));}
  String note(int midi){return EN[Math.floorMod(midi,12)]+(Math.floorDiv(midi,12)-1);}
  TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);return t;}
  LinearLayout panel(){LinearLayout p=new LinearLayout(this);p.setOrientation(1);p.setPadding(dp(20),dp(20),dp(20),dp(20));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(24));p.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));root.addView(p,lp);return p;}
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(244,247,252));getWindow().setNavigationBarColor(Color.rgb(244,247,252));getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(Color.rgb(244,247,252));root=new LinearLayout(this);root.setPadding(dp(18),dp(24),dp(18),dp(30));root.setOrientation(1);sc.addView(root);setContentView(sc);
    root.addView(text("VOICE TRAINING STUDIO",12,MUTED,true));root.addView(text("Pitch Note ♫",32,NAVY,true));TextView sub=text("피아노를 듣고, 내 음정을 눈으로 확인하세요.",14,MUTED,false);sub.setPadding(0,0,0,dp(22));root.addView(sub);
    LinearLayout a=panel();a.addView(text("목표 음정",13,MUTED,false));target=text("C4  도   ·   261.6 Hz",25,NAVY,true);a.addView(target);TextView myLabel=text("내 목소리",13,MUTED,false);myLabel.setPadding(0,dp(22),0,0);a.addView(myLabel);current=text("—",48,NAVY,true);a.addView(current);difference=text("차이 — cent",15,MUTED,false);a.addView(difference);status=text("마이크를 켜고 노래해 보세요",15,BLUE,true);status.setPadding(0,dp(18),0,dp(15));a.addView(status);
    LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);micButton=text("🎤 마이크 켜기",16,Color.WHITE,true);micButton.setGravity(Gravity.CENTER);micButton.setBackground(round(BLUE,16));micButton.setPadding(dp(12),dp(16),dp(12),dp(16));actions.addView(micButton,new LinearLayout.LayoutParams(0,-2,2));micButton.setOnClickListener(v->toggleMic());TextView reset=text("초기화",15,NAVY,true);reset.setGravity(Gravity.CENTER);reset.setBackground(round(Color.rgb(237,243,253),16));LinearLayout.LayoutParams resetLp=new LinearLayout.LayoutParams(0,-1,1);resetLp.leftMargin=dp(8);actions.addView(reset,resetLp);reset.setOnClickListener(v->{synchronized(samples){samples.clear();}graph.invalidate();});a.addView(actions);
    LinearLayout p=panel();p.addView(text("실시간 피치 모니터 · 최근 12초",18,NAVY,true));graph=new PitchGraph();p.addView(graph,new LinearLayout.LayoutParams(-1,dp(220)));p.addView(text("파란 선은 목소리의 음높이, 점선은 목표 음정입니다.",12,MUTED,false));
    LinearLayout keys=panel();keys.addView(text("피아노 건반 · 누르는 동안 소리 유지",17,NAVY,true));Spinner oct=new Spinner(this);String[] octs={"3옥타브 C3–B3","4옥타브 C4–B4","5옥타브 C5–B5"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,octs);oct.setAdapter(adapter);keys.addView(oct);keyboard=new Keyboard();keys.addView(keyboard,new LinearLayout.LayoutParams(-1,dp(190)));oct.setSelection(1);oct.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){keyboard.base=(pos+4)*12;selectNote(keyboard.base);keyboard.invalidate();}});
    TextView tip=text("건반을 누르고 있으면 해당 음이 계속 울립니다. 손을 떼면 멈춥니다. 이어서 같은 음을 불러보세요.\n\n마이크 음성은 기기에서만 분석하며 저장하거나 전송하지 않습니다.",12,MUTED,false);tip.setPadding(0,dp(12),0,0);keys.addView(tip);
  }
  android.graphics.drawable.GradientDrawable round(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  void selectNote(int m){targetMidi=m;target.setText(note(m)+"  "+NOTES[Math.floorMod(m,12)]+"   ·   "+String.format(java.util.Locale.US,"%.1f Hz",hz(m)));if(keyboard!=null)keyboard.invalidate();if(graph!=null)graph.invalidate();}
  void toggleMic(){if(recording){stopMic();return;}if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},5);return;}startMic();}
  @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==5&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startMic();else status.setText("설정에서 마이크 권한을 허용해 주세요.");}
  void startMic() {
    if (recording) return;
    final int rate = 44100;
    final int minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT);
    AudioRecord input = null;
    try {
      if (minBuffer <= 0) throw new IllegalStateException();
      input = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
          AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer);
      input.startRecording();
      if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
        throw new IllegalStateException();
      }
    } catch (RuntimeException e) {
      if (input != null) input.release();
      status.setText("마이크를 시작할 수 없어요. 권한과 마이크 설정을 확인해 주세요.");
      return;
    }
    final AudioRecord session = input;
    recorder = session;
    recording = true;
    synchronized (samples) { samples.clear(); }
    micButton.setText("■ 마이크 끄기");
    status.setTextColor(MUTED);
    status.setText("목소리를 듣고 있어요");
    new Thread(() -> analyzeMic(session, rate), "pitch-analyzer").start();
  }

  void analyzeMic(AudioRecord input, int rate) {
    final int hop = rate / 50;
    short[] window = new short[hop * 2];
    double[] differenceCurve = new double[rate / 65 + 1];
    int filled = 0, quietFrames = 0;
    try {
      while (recorder == input) {
        int count = input.read(window, filled, window.length - filled);
        if (count < 0) throw new IllegalStateException();
        filled += count;
        if (filled < window.length) continue;
        double frequency = detect(window, rate, differenceCurve);
        System.arraycopy(window, hop, window, 0, hop);
        filled = hop;
        quietFrames = frequency > 0 ? 0 : quietFrames + 1;
        if (quietFrames > 0 && quietFrames < 5) continue;
        if (quietFrames > 5) continue;
        double midi = frequency > 0 ? 69 + 12 * Math.log(frequency / 440) / Math.log(2) : Double.NaN;
        long now = SystemClock.elapsedRealtime();
        runOnUiThread(() -> {
          if (recorder != input) return;
          synchronized (samples) {
            samples.addLast(new float[]{now, (float) midi});
            while (!samples.isEmpty() && now - samples.peekFirst()[0] > 12000) samples.removeFirst();
          }
          if (frequency > 0) {
            int cents = (int) Math.round((midi - targetMidi) * 100);
            current.setText(note((int) Math.round(midi)));
            difference.setText(String.format(Locale.US, "%+d cent · %.1f Hz", cents, frequency));
            status.setText(Math.abs(cents) <= 15 ? "✓ 정확한 음정이에요!"
                : cents < 0 ? "조금 더 높게 불러보세요 ↑" : "조금 더 낮게 불러보세요 ↓");
            status.setTextColor(Math.abs(cents) <= 15 ? Color.rgb(27,158,110)
                : cents < 0 ? BLUE : Color.rgb(218,89,105));
          } else {
            current.setText("—");
            difference.setText("차이 — cent");
            status.setTextColor(MUTED);
            status.setText("목소리를 듣고 있어요");
          }
          graph.invalidate();
        });
      }
    } catch (RuntimeException e) {
      runOnUiThread(() -> {
        if (recorder != input) return;
        stopMic();
        status.setText("마이크 입력이 끊겼어요. 다시 켜 주세요.");
      });
    } finally {
      input.release();
    }
  }

  double detect(short[] window, int rate, double[] curve) {
    double power = 0;
    for (short sample : window) power += (double) sample * sample;
    if (power < window.length * 80.0 * 80.0) return -1;

    int maxLag = curve.length - 1;
    double cumulative = 0;
    curve[0] = 1;
    for (int lag = 1; lag <= maxLag; lag++) {
      double sum = 0;
      for (int i = 0; i < window.length - maxLag; i += 2) {
        int delta = window[i] - window[i + lag];
        sum += (double) delta * delta;
      }
      cumulative += sum;
      curve[lag] = cumulative > 0 ? sum * lag / cumulative : 1;
    }
    for (int lag = rate / 1200; lag < maxLag; lag++) {
      if (curve[lag] >= .18) continue;
      while (lag < maxLag && curve[lag + 1] < curve[lag]) lag++;
      if (lag == maxLag) return (double) rate / lag;
      double a = curve[lag - 1], b = curve[lag], c = curve[lag + 1];
      double offset = Math.max(-.5, Math.min(.5, .5 * (a - c) / (a - 2*b + c + 1e-9)));
      return rate / (lag + offset);
    }
    return -1;
  }

  void stopMic() {
    AudioRecord input = recorder;
    recorder = null;
    recording = false;
    if (input != null) {
      try { input.stop(); } catch (IllegalStateException ignored) {}
    }
    micButton.setText("🎤 마이크 켜기");
    current.setText("—");
    difference.setText("차이 — cent");
    status.setTextColor(MUTED);
    status.setText("마이크가 꺼졌어요");
  }
  void startTone(int pointer,int midi){stopTone(pointer);Tone tone=new Tone(midi);tones.put(pointer,tone);audioPool.execute(tone);}
  void stopTone(int pointer){Tone t=tones.remove(pointer);if(t!=null)t.running=false;}
  class Tone implements Runnable {volatile boolean running=true;final int midi;Tone(int m){midi=m;}public void run(){int sr=22050;AudioTrack track=null;try{int buffer=Math.max(4096,AudioTrack.getMinBufferSize(sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT));track=new AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer,AudioTrack.MODE_STREAM);track.play();short[] wave=new short[1024];double phase=0,step=2*Math.PI*hz(midi)/sr;long index=0;while(running){for(int i=0;i<wave.length;i++){double env=Math.min(1,(index++)/(sr*.02));double s=Math.sin(phase)+.22*Math.sin(phase*2)+.08*Math.sin(phase*3);wave[i]=(short)(s*env*6500);phase+=step;if(phase>Math.PI*2)phase-=Math.PI*2;}track.write(wave,0,wave.length);}Arrays.fill(wave,(short)0);track.write(wave,0,wave.length);}catch(Exception ignored){}finally{if(track!=null){try{track.stop();}catch(Exception ignored){}track.release();}}}}
  class Keyboard extends View {int base=60;Paint p=new Paint(3);final int[] white={0,2,4,5,7,9,11};final int[] black={1,3,6,8,10};final int[] blackAfter={1,2,4,5,6};final HashMap<Integer,Integer> pointers=new HashMap<>();Keyboard(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth()/7f,h=getHeight();for(int i=0;i<7;i++){int m=base+white[i];p.setColor(pointers.containsValue(m)?Color.rgb(198,220,255):Color.WHITE);c.drawRoundRect(i*w+1,1,(i+1)*w-1,h-2,dp(6),dp(6),p);p.setColor(Color.rgb(207,217,233));p.setStyle(Paint.Style.STROKE);c.drawRoundRect(i*w+1,1,(i+1)*w-1,h-2,dp(6),dp(6),p);p.setStyle(Paint.Style.FILL);p.setColor(MUTED);p.setTextSize(dp(12));c.drawText(NOTES[white[i]],i*w+w*.27f,h-dp(14),p);}for(int i=0;i<5;i++){float x=blackAfter[i]*w;int m=base+black[i];p.setColor(pointers.containsValue(m)?BLUE:NAVY);c.drawRoundRect(x-w*.30f,0,x+w*.30f,h*.60f,dp(5),dp(5),p);}}int hit(float x,float y){float w=getWidth()/7f;for(int i=0;i<5;i++){float center=blackAfter[i]*w;if(y<getHeight()*.60f&&x>=center-w*.30f&&x<=center+w*.30f)return base+black[i];}return base+white[Math.min(6,Math.max(0,(int)(x/w)))];}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){int action=e.getActionMasked(),idx=e.getActionIndex(),id=e.getPointerId(idx);if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){int m=hit(e.getX(idx),e.getY(idx));pointers.put(id,m);selectNote(m);startTone(id,m);invalidate();return true;}if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){if(action==MotionEvent.ACTION_CANCEL){for(int key:new ArrayList<>(pointers.keySet()))stopTone(key);pointers.clear();}else{stopTone(id);pointers.remove(id);}invalidate();return true;}return true;}}
  class PitchGraph extends View {Paint p=new Paint(3);PitchGraph(){super(MainActivity.this);}protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();c.drawColor(Color.rgb(248,250,254));int center=targetMidi;for(int i=-8;i<=8;i++){float y=h/2-i*h/16;p.setColor(i==0?Color.rgb(110,153,229):Color.rgb(224,232,243));p.setStrokeWidth(dp(i==0?2:1));p.setPathEffect(i==0?new android.graphics.DashPathEffect(new float[]{dp(7),dp(6)},0):null);c.drawLine(0,y,w,y,p);}p.setPathEffect(null);p.setColor(BLUE);p.setStrokeWidth(dp(2.5f));p.setStyle(Paint.Style.STROKE);Path path=new Path();boolean started=false;long now=SystemClock.elapsedRealtime();synchronized(samples){for(float[] sample:samples){if(Float.isNaN(sample[1])){started=false;continue;}float x=w-(now-sample[0])/12000f*w,y=h/2-(sample[1]-center)*h/16;if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);}}c.drawPath(path,p);p.setStyle(Paint.Style.FILL);p.setColor(MUTED);p.setTextSize(dp(12));c.drawText(note(center),dp(8),h/2-dp(10),p);}}
  @Override protected void onPause(){super.onPause();for(Integer id:new ArrayList<>(tones.keySet()))stopTone(id);if(recording)stopMic();}
  @Override protected void onDestroy(){super.onDestroy();audioPool.shutdownNow();}
}
