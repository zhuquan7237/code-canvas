package com.nous.codecanvas;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.test.InstrumentationTestCase;
import android.view.ContextThemeWrapper;
import android.widget.EditText;
import com.nous.codecanvas.ui.MainActivity;
import com.nous.codecanvas.ui.UiDialogHelper;
/** Exercise BOTH resource configurations without changing global device state. */
public class NamingDialogThemeContrastTest extends InstrumentationTestCase {
 private static double luminance(int c){double[] channels={Color.red(c)/255.0,Color.green(c)/255.0,Color.blue(c)/255.0};for(int i=0;i<3;i++)channels[i]=channels[i]<=0.04045?channels[i]/12.92:Math.pow((channels[i]+0.055)/1.055,2.4);return channels[0]*0.2126+channels[1]*0.7152+channels[2]*0.0722;}
 private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return(Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);} public void testLightThemeRequestsReadableSystemBarIcons() throws Throwable {
  MainActivity a=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try {runTestOnUiThread(() -> {
   Configuration c=new Configuration(a.getResources().getConfiguration());c.uiMode=(c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_NO;
   ContextThemeWrapper theme=new ContextThemeWrapper(a,R.style.AppTheme);theme.applyOverrideConfiguration(c);
   android.util.TypedValue v=new android.util.TypedValue();
   assertTrue(theme.getTheme().resolveAttribute(android.R.attr.windowLightStatusBar,v,true));
   assertTrue("Light background needs dark status icons",v.data!=0);
  });}finally{getInstrumentation().runOnMainSync(a::finish);}
 }
 public void testBothLightAndDarkActualNamingContrast() throws Throwable {
  MainActivity a=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{
   final int[] resolved={0,0};
   for(int mode:new int[]{Configuration.UI_MODE_NIGHT_NO,Configuration.UI_MODE_NIGHT_YES}){
    runTestOnUiThread(() -> {
     Configuration c=new Configuration(a.getResources().getConfiguration()); c.uiMode=(c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)|mode;
     ContextThemeWrapper theme=new ContextThemeWrapper(a,R.style.AppTheme);theme.applyOverrideConfiguration(c);
     AlertDialog d=UiDialogHelper.createThemedInputDialog(theme,"新建画布","例如 作品.svg","我的作品.html",null);
     try{
      d.show();EditText input=d.findViewById(R.id.dialog_edit_input);
      int bg=theme.getResources().getColor(R.color.canvas_surface_subtle);
      assertTrue("Naming surface must be fully opaque, not a transparent hole",(bg>>>24)==255);
      resolved[mode==Configuration.UI_MODE_NIGHT_YES?1:0]=bg;
      double text=contrast(input.getCurrentTextColor(),bg),hint=contrast(input.getCurrentHintTextColor(),bg);
      assertTrue("Text contrast "+text,text>=4.5);assertTrue("Hint contrast "+hint,hint>=4.5);
      assertNotNull(d.findViewById(R.id.dialog_btn_positive));assertNotNull(d.findViewById(R.id.dialog_btn_negative));
     }finally{d.dismiss();}
    });
   }
   assertTrue("Day and night must resolve to different naming surfaces (the night qualifier really applied)",
           resolved[0]!=0&&resolved[1]!=0&&resolved[0]!=resolved[1]);
  }finally{getInstrumentation().runOnMainSync(a::finish);}
 }
}

