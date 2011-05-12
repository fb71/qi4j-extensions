package org.qi4j.entitystore.lucene;

import java.io.File;
import java.io.Serializable;


public final class LuceneEntityStoreInfo
        implements Serializable {

    private File        dir;

    public LuceneEntityStoreInfo( File dir ) {
        this.dir = dir;
    }

    public File getDir() {
        return dir;
    }
    
}