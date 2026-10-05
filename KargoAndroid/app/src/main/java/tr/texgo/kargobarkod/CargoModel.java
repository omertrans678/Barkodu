package tr.texgo.kargobarkod;

import java.util.*;

public final class CargoModel {
    public static final String[] NAMES = {"DHL/MNG", "HepsiJet", "Tex", "Aras", "Bilinmeyen"};
    public static final String[][] PREFIXES = {{"416", "HTS"}, {"416", "H0", "HTS"}, {"73"}, {"72", "P0", "A0", "FL0"}};
    public static final int UNKNOWN=4;
    public int selected=-1, active=0;
    public final ArrayList<Item> items=new ArrayList<>();
    private final ArrayDeque<Change> history=new ArrayDeque<>();
    public static final class Item {
        public String code; public int cargo, quantity;
        public Item(String code,int cargo,int quantity){this.code=code;this.cargo=cargo;this.quantity=quantity;}
        public Item copy(){return new Item(code,cargo,quantity);}
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
        String code=normalize(raw);if(selected<0||selected>3||!valid(code))throw new IllegalArgumentException("Önce kargo seçin ve geçerli barkod girin.");
        int cargo=classify(code,selected),index=-1;
        for(int i=0;i<items.size();i++)if(items.get(i).cargo==cargo&&items.get(i).code.equals(code)){index=i;break;}
        Change c=new Change();c.code=code;c.cargo=cargo;c.index=index;c.previous=index>=0?items.get(index).copy():null;push(c);
        Item next=index>=0?items.remove(index):new Item(code,cargo,0);next.quantity++;items.add(0,next);active=cargo;return next;
    }
    public void remove(Item item){int index=items.indexOf(item);if(index<0)return;Change c=new Change();c.previous=item.copy();c.index=index;c.deletion=true;push(c);items.remove(index);}
    public void clear(){Change c=new Change();c.all=new ArrayList<>();for(Item item:items)c.all.add(item.copy());push(c);items.clear();}
    public boolean canUndo(){return !history.isEmpty();}
    public boolean undo(){
        if(history.isEmpty())return false;Change c=history.removeLast();
        if(c.all!=null){items.clear();items.addAll(c.all);}
        else if(c.deletion)items.add(Math.min(c.index,items.size()),c.previous);
        else {for(int i=items.size()-1;i>=0;i--)if(items.get(i).cargo==c.cargo&&items.get(i).code.equals(c.code))items.remove(i);if(c.previous!=null)items.add(Math.min(c.index,items.size()),c.previous);}
        active=c.active;return true;
    }
    public ArrayList<Item> visible(){ArrayList<Item> result=new ArrayList<>();for(Item x:items)if(x.cargo==active)result.add(x);return result;}
    public int count(int cargo){int n=0;for(Item x:items)if(x.cargo==cargo)n++;return n;}
    public String text(){StringBuilder out=new StringBuilder();for(int cargo=0;cargo<NAMES.length;cargo++){if(count(cargo)==0)continue;if(out.length()>0)out.append("\n\n");out.append('[').append(NAMES[cargo]).append("]\n");for(Item x:items)if(x.cargo==cargo){out.append(x.code);if(x.quantity>1)out.append(" (").append(x.quantity).append(" adet)");out.append('\n');}}return out.toString();}
}
