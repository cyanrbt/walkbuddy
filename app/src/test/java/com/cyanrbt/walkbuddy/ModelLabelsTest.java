package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ModelLabelsTest {
    private String asset(String name) throws IOException {
        Path root=Paths.get("").toAbsolutePath();while(!Files.isDirectory(root.resolve("app/src/main/assets")))root=root.getParent();
        return new String(Files.readAllBytes(root.resolve("app/src/main/assets/"+name)),StandardCharsets.UTF_8);
    }
    private ModelLabels read(String translated) throws IOException {
        return ModelLabels.read(new StringReader(asset("labelmap.txt")),new StringReader(translated));
    }
    private void reject(String translated) throws IOException {
        try{read(translated);fail("Invalid translation changed the model-slot contract");}catch(IOException expected){/* rejected */}
    }
    @Test public void allBundledNamesRetainTheOriginalModelSlots() throws Exception {
        ModelLabels labels=read(asset("labelmap_zh.tsv"));String[] original=asset("labelmap.txt").split("\n");
        Set<String> unique=new HashSet<>();int defined=0,unused=0;
        assertEquals(90,labels.size());
        for(int i=0;i<labels.size();i++) {
            assertEquals(original[i],labels.category(i));
            if(labels.defined(i)) {
                assertTrue(labels.displayName(i),labels.displayName(i).matches("[\\u4e00-\\u9fff]+"));
                assertTrue(unique.add(labels.displayName(i)));defined++;
            }else{assertEquals("???",labels.displayName(i));unused++;}
        }
        assertEquals(80,defined);assertEquals(10,unused);
        assertEquals("红绿灯",labels.displayName(9));assertEquals("雨伞",labels.displayName(27));
        assertEquals("手机",labels.displayName(76));assertEquals("牙刷",labels.displayName(89));
    }
    @Test public void missingExtraOrReorderedRowsCannotSilentlyShiftCategories() throws Exception {
        String table=asset("labelmap_zh.tsv");String[] rows=table.split("\n");
        reject(table.substring(table.indexOf('\n')+1));reject(table+"extra\t额外类别\n");
        String first=rows[0];rows[0]=rows[1];rows[1]=first;reject(String.join("\n",rows));
    }
    @Test public void emptyEnglishDuplicateAndInventedPlaceholderNamesAreRejected() throws Exception {
        String table=asset("labelmap_zh.tsv");
        reject(table.replace("person\t人","person\t"));reject(table.replace("person\t人","person\tperson"));
        reject(table.replace("bicycle\t自行车","bicycle\t人"));
        reject(table.replace("???\t???","???\t锥桶"));reject(table.replace("person\t人","person\t???"));
    }
}
