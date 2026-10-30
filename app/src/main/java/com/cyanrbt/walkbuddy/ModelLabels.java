package com.cyanrbt.walkbuddy;

import java.io.*;
import java.util.*;

/** Display names stay anchored to the original 90 model slots, including unused slots. */
final class ModelLabels {
    private final List<String> categories,display;
    private ModelLabels(List<String> categories,List<String> display) {
        this.categories=Collections.unmodifiableList(categories);this.display=Collections.unmodifiableList(display);
    }
    static ModelLabels read(Reader original,Reader translated) throws IOException {
        BufferedReader source=new BufferedReader(original),names=new BufferedReader(translated);
        List<String> categories=new ArrayList<>(),display=new ArrayList<>();Set<String> unique=new HashSet<>();
        String category,line;
        while((category=source.readLine())!=null) {
            line=names.readLine();String[] parts=line==null?new String[0]:line.split("\t",-1);
            if(parts.length!=2 || !category.equals(parts[0]) || parts[1].isEmpty() || !parts[1].equals(parts[1].trim()))
                throw new IOException("中文标签槽位不匹配："+categories.size());
            boolean unused="???".equals(category);
            if(unused?!"???".equals(parts[1]):"???".equals(parts[1]) || category.equals(parts[1]) || !unique.add(parts[1]))
                throw new IOException("中文标签缺失或重复："+categories.size());
            categories.add(category);display.add(parts[1]);
        }
        if(names.readLine()!=null || categories.size()!=90 || unique.size()!=80 || !"person".equals(categories.get(0)))
            throw new IOException("模型标签映射不匹配");
        return new ModelLabels(categories,display);
    }
    int size(){return categories.size();}
    String category(int index){return categories.get(index);}
    String displayName(int index){return display.get(index);}
    boolean defined(int index){return !"???".equals(categories.get(index));}
}
