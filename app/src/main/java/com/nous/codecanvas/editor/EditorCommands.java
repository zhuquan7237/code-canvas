package com.nous.codecanvas.editor;
public final class EditorCommands {
 public static final int MAX=2*1024*1024;
 public static class Result {public final String text; public final int cursor; public Result(String t,int c){text=t;cursor=c;}}
 public static Result insert(String text,int start,int end,String symbol) {
  start=Math.max(0,Math.min(start,text.length()));end=Math.max(start,Math.min(end,text.length()));
  String v=symbol.equals("Tab")?"    ":symbol;
  String t=text.substring(0,start)+v+text.substring(end);if(t.length()>MAX)throw new IllegalArgumentException("代码超过 2MB 限制");
  return new Result(t,start+((v.equals("{}")||v.equals("()")||v.equals("[]")||v.equals("\"\""))?1:v.length()));
 }
 public static String replaceAll(String text,String find,String replacement) {
  if(find.isEmpty())throw new IllegalArgumentException("请输入查找内容");
  int count=0,pos=0;while((pos=text.indexOf(find,pos))>=0){count++;pos+=find.length();}
  long size=(long)text.length()+(long)count*(replacement.length()-find.length());if(size>MAX)throw new IllegalArgumentException("替换后超过 2MB 限制");
  return text.replace(find,replacement);
 }
 public static int lineOffset(String text,int line){if(line<1)throw new IllegalArgumentException("行号至少为 1");int pos=0;for(int n=1;n<line;n++){int next=text.indexOf('\n',pos);if(next<0)return text.length();pos=next+1;}return pos;}
}
