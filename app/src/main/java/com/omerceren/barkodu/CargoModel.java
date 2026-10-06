package com.omerceren.barkodu;

import java.util.*;

public final class CargoModel {
    public static final String[] NAMES = {"DHL", "JET", "TEX", "ARAS", "Unknown"};
    public static final String[][] PREFIXES = {{"416", "HTS"}, {"416", "H0", "HTS"}, {"73"}, {"72", "P0", "A0", "FL0"}};
    public static final int UNKNOWN=4;
    public int selected=-1, active=0;
    public final ArrayList<Item> items=new ArrayList<>();
    private final ArrayDeque<Change> history=new ArrayDeque<>();
    public static final class Item {
        private static long nextId;
        public long id = ++nextId;
        public String code; public int cargo, quantity;
        public Item(String code,int cargo,int quantity){this.code=code;this.cargo=cargo;this.quantity=quantity;}
        public Item copy(){Item copy=new Item(code,cargo,quantity);copy.id=id;return copy;}
    }
    private static final class Change {String code;int cargo,index,active;Item previous;ArrayList<Item> all;boolean deletion;}
    public static String normalize(String code){return code==null?"":code.trim().toUpperCase(Locale.ROOT);}
    public static boolean valid(String code){return normalize(code).matches("[A-Z0-9]{3,64}");}
    public static int classify(String code,int selected){
        if(selected>=0 && selected<PREFIXES.length) for(String prefix:PREFIXES[selected]) if(normalize(code).startsWith(prefix))return selected;
        return UNKNOWN;
    }
    private void push(Change c){c.active=active;history.addLast(c);if(history.size()>100)history.removeFirst();}
    public Item add(String raw){
        return addForCarrier(raw, selected);
    }
    public Item addForCarrier(String raw, int carrier){
        String code=normalize(raw);if(carrier<0||carrier>3||!valid(code))throw new IllegalArgumentException("Select a carrier and enter a valid barcode.");
        int cargo=classify(code,carrier);
        Item next=new Item(code,cargo,1);
        Change c=new Change();c.previous=next.copy();push(c);
        items.add(0,next);active=cargo;return next;
    }
    public void remove(Item item){int index=items.indexOf(item);if(index<0)return;Change c=new Change();c.previous=item.copy();c.index=index;c.deletion=true;push(c);items.remove(index);}
    public void restore(Item item,int index){for(Item stored:items)if(stored.id==item.id)return;items.add(Math.min(index,items.size()),item);}
    public void clear(){Change c=new Change();c.all=new ArrayList<>();for(Item item:items)c.all.add(item.copy());push(c);items.clear();}
    public boolean canUndo(){return !history.isEmpty();}
    public boolean undo(){
        if(history.isEmpty())return false;Change c=history.removeLast();
        if(c.all!=null){items.clear();items.addAll(c.all);}
        else if(c.deletion)items.add(Math.min(c.index,items.size()),c.previous);
        else {for(int i=items.size()-1;i>=0;i--)if(items.get(i).id==c.previous.id){items.remove(i);break;}}
        active=c.active;return true;
    }
    public ArrayList<Item> visible(){ArrayList<Item> result=new ArrayList<>();for(Item x:items)if(x.cargo==active)result.add(x);return result;}
    public int count(int cargo){int n=0;for(Item x:items)if(x.cargo==cargo)n++;return n;}
    public String text(){StringBuilder out=new StringBuilder();for(int cargo=0;cargo<NAMES.length;cargo++){if(count(cargo)==0)continue;if(out.length()>0)out.append("\n\n");out.append('[').append(NAMES[cargo]).append("]\n");for(Item x:items)if(x.cargo==cargo){out.append(x.code);if(x.quantity>1)out.append(" (").append(x.quantity).append(" pcs)");out.append('\n');}}return out.toString();}
}
