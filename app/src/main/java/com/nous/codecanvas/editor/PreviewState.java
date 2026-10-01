package com.nous.codecanvas.editor;
public final class PreviewState {
 public long generation; private String key; public boolean pending;
 public boolean request(String next,boolean force){if(!force && next.equals(key))return false;key=next;generation++;pending=true;return true;}
 public boolean ready(long ticket){if(ticket!=generation)return false;pending=false;return true;}
 public void invalidate(){key=null;generation++;pending=false;}
}
