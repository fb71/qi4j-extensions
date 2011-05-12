/* 
 * polymap.org
 * Copyright 2011, Falko Bräutigam, and individual contributors as indicated
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package org.qi4j.entitystore.lucene;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.io.File;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;

import org.qi4j.api.entity.EntityReference;
import org.qi4j.api.injection.scope.This;
import org.qi4j.api.injection.scope.Uses;
import org.qi4j.api.service.Activatable;
import org.qi4j.api.structure.Module;
import org.qi4j.api.unitofwork.EntityTypeNotFoundException;
import org.qi4j.api.unitofwork.NoSuchEntityException;
import org.qi4j.api.usecase.Usecase;
import org.qi4j.spi.entity.EntityDescriptor;
import org.qi4j.spi.entity.EntityState;
import org.qi4j.spi.entitystore.EntityStore;
import org.qi4j.spi.entitystore.EntityStoreException;
import org.qi4j.spi.entitystore.EntityStoreSPI;
import org.qi4j.spi.entitystore.EntityStoreUnitOfWork;
import org.qi4j.spi.entitystore.StateCommitter;
import org.qi4j.spi.service.ServiceDescriptor;
import org.qi4j.spi.structure.ModuleSPI;

/**
 * Implementation of EntityStore backed by Apache Lucene.
 *
 */
@SuppressWarnings("deprecation")
public class LuceneEntityStoreMixin
    implements LuceneSearcher, Activatable, EntityStore, EntityStoreSPI {

    private static Log log = LogFactory.getLog( LuceneEntityStoreMixin.class );
    
    private static final int        MAX_FIELD_SIZE = 1024*1024;

    
    private @This EntityStoreSPI    entityStoreSpi;

    private @Uses ServiceDescriptor descriptor;
    
    protected String                uuid;
    
    private int                     count;
    
    private FSDirectory             directory;

    private IndexReader             indexReader;
    
    private IndexSearcher           searcher;
    
    /**
     * Synchronize access to the indexReader: allow write only without a reader;
     * multiple readers, one writer.
     */
    private ReentrantReadWriteLock  rwLock = new ReentrantReadWriteLock( false );

    private Analyzer                analyzer = new WhitespaceAnalyzer();

    
    public IndexSearcher getIndexSearcher() {
        return searcher;
    }
    
    
    public void activate()
    throws Exception {
        File indexDir = getApplicationRoot();
        log.debug( "Lucene store: " + indexDir.getAbsolutePath() );
        uuid = UUID.randomUUID().toString() + "-";

        if (indexDir != null) {
            log.info( "    creating directory: " + indexDir );
            indexDir.mkdirs();
            directory = FSDirectory.open( indexDir );
            log.info( "    files in directry: " + Arrays.asList( directory.listAll() ) );
        }
        
        if (directory.listAll().length == 0) {
            IndexWriter iwriter = new IndexWriter( directory, analyzer, true, new IndexWriter.MaxFieldLength( MAX_FIELD_SIZE ) );
            iwriter.close();
        }
        
        // check expunge deletes
        log.info( "Expunge pending deletions..." );
        long start = System.currentTimeMillis();
        IndexWriter iwriter = new IndexWriter( directory, analyzer, false, 
                new IndexWriter.MaxFieldLength( MAX_FIELD_SIZE ) );
        try {
            log.info( "    hasDeletions: " + iwriter.hasDeletions() );
            iwriter.expungeDeletes( true );
            iwriter.commit();
            log.info( "    done. (" + (System.currentTimeMillis() - start) + "ms)" );
        }
        catch (Exception e) {
            log.warn( "Error during expungeDeletions().", e );
        }
        finally {
            iwriter.close();
        }

        // open reader
        indexReader = IndexReader.open( directory, false ); // read-only=true
        searcher = new IndexSearcher( indexReader ); // read-only=true
        
//        // listen to model changes
//        modelListener = new GlobalModelChangeListener() {
//            public void modelChanged( GlobalModelChangeEvent ev ) {
//                if (ev.getEventType() == EventType.commit) {
//                    try {
//                        Set<IMap> maps = new HashSet();
//                        for (ILayer layer : PoiIndexer.this.provider.findLayers()) {
//                            if (ev.hasChanged( layer )) {
//                                reindex();
//                                return;
//                            }
//                            maps.add( layer.getMap() );
//                        }
//                        for (IMap map : maps) {
//                            if (ev.hasChanged( map )) {
//                                reindex();
//                                return;
//                            }
//                        }
//                    }
//                    catch (Exception e) {
//                        PolymapWorkbench.handleError( LKAPlugin.PLUGIN_ID, this, "Fehler beim ReIndizieren der POIs.", e );
//                    }
//                }
//            }
//            public boolean isValid() {
//                return true;
//            }
//        };
//        ProjectRepository.globalInstance().addGlobalModelChangeListener( modelListener );
    }


    private File getApplicationRoot() {
        LuceneEntityStoreInfo storeInfo = descriptor.metaInfo( LuceneEntityStoreInfo.class );
        if (storeInfo == null) {
            throw new IllegalStateException( "No dir for LuceneEntityStore" );
        }
        else {
            return storeInfo.getDir();
        }
    }


    public void passivate()
    throws Exception {
        // XXX rwLock !?
        searcher.close();
        indexReader.close();
    }


    public EntityStoreUnitOfWork newUnitOfWork( Usecase usecase, Module module ) {
        return new LuceneEntityStoreUnitOfWork( entityStoreSpi, newUnitOfWorkId(), module );
    }


    public EntityStoreUnitOfWork visitEntityStates( EntityStateVisitor visitor,
            Module moduleInstance ) {
        throw new RuntimeException( "not yet implemented." );
//        final DefaultEntityStoreUnitOfWork uow = new DefaultEntityStoreUnitOfWork( entityStoreSpi,
//                newUnitOfWorkId(), moduleInstance );
//
//        try {
//            String[] identities = dir.list();
//            for (String identity : identities) {
//                EntityState entityState = uow.getEntityState( EntityReference
//                        .parseEntityReference( identity ) );
//                visitor.visitEntityState( entityState );
//            }
//        }
//        catch (/*BackingStore*/Exception e) {
//            throw new EntityStoreException( e );
//        }
//        
//        return uow;
    }


    public EntityState newEntityState( EntityStoreUnitOfWork unitOfWork, EntityReference identity,
            EntityDescriptor entityDescriptor ) {
        return new LuceneEntityState( (LuceneEntityStoreUnitOfWork)unitOfWork, identity, entityDescriptor );
    }


    public EntityState getEntityState( EntityStoreUnitOfWork unitOfWork, EntityReference identity ) {
        try {
            rwLock.readLock().lock();
            
            LuceneEntityStoreUnitOfWork uow = (LuceneEntityStoreUnitOfWork)unitOfWork;
            ModuleSPI module = (ModuleSPI)uow.module();

            BooleanQuery query = new BooleanQuery();
            query.add( new TermQuery( new Term( "identity", identity.identity() ) ), BooleanClause.Occur.MUST );
            //log.debug( "    ===> Lucene query: " + query );

            ScoreDoc[] scoreDocs = searcher.search( query, null, 1 ).scoreDocs;
            
            if (scoreDocs.length < 1) {
                throw new NoSuchEntityException( identity );
            }
            else {
                Document doc = searcher.doc( scoreDocs[0].doc );

                String typeName = doc.get( "type" );
                if (typeName != null) {
                    EntityDescriptor entityDescriptor = module.entityDescriptor( typeName );
                    if (entityDescriptor != null) {
                        return new LuceneEntityState( uow, identity, entityDescriptor, doc );
                    }
                }
                throw new EntityTypeNotFoundException( typeName );

            }
        }
        catch (IOException e) {
            throw new EntityStoreException( e );
        }
        finally {
            rwLock.readLock().unlock();
        }
    }


    public StateCommitter apply( final Iterable<EntityState> states, final String version ) {

        return new StateCommitter() {

            public void commit() {
                log.info( "Committing..." );
                long start = System.currentTimeMillis();

                IndexWriter iwriter = null;
                try {
                    rwLock.writeLock().lock();
                    
                    iwriter = new IndexWriter( directory, analyzer, false, new IndexWriter.MaxFieldLength( MAX_FIELD_SIZE ) );

                    for (EntityState entityState : states) {
                        LuceneEntityState state = (LuceneEntityState)entityState;
                        
                        Term idTerm = new Term( "identity", state.identity().identity() );
                        
                        switch (state.status()) {
                            case NEW : {
                                Document doc = state.writeBack( version );
                                iwriter.addDocument( doc );
                                //log.debug( "    added: " + doc );
                                break;
                            }
                            case UPDATED : {
                                Document doc = state.writeBack( version );
                                iwriter.updateDocument( idTerm, doc );
                                log.debug( "    updated: " + doc );
                                break;
                            }
                            case REMOVED : {
                                iwriter.deleteDocuments( idTerm );
                                log.debug( "    deleted: " + idTerm );
                                break;
                            }
                            default : {
                                //log.debug( "    ommitting: " + state.identity().identity() + ", Status= " + state.status() + ", Doc= " + state.writeBack( version ) );
                            }
                        }
                    }
                    
                    iwriter.commit();
                    iwriter.close();
                    log.info( "...done. (" + (System.currentTimeMillis()-start) + "ms)" );

                    start = System.currentTimeMillis();
//                    indexReader.reopen();
//                    searcher = new IndexSearcher( indexReader );

                    // XXX hack to get index reloaded
                    log.info( "    creating new index reader..." );
                    searcher.close();
                    indexReader.close();
                    
                    indexReader = IndexReader.open( directory, false ); // read-only=true
                    log.info( "    creating index searcher..." );
                    searcher = new IndexSearcher( indexReader ); // read-only=true
                    log.info( "...reopen index reader done. (" + (System.currentTimeMillis()-start) + "ms)" );
                }
                catch (Exception e) {
                    throw new RuntimeException( e );
                }
                finally {
                    try {
                        rwLock.writeLock().unlock();
                    }
                    catch (Exception e) {
                        // the writeLock was not aquired, should never happen
                        log.warn( e.getLocalizedMessage(), e );
                    }
                    
                    if (iwriter != null) {
                        try {
                            iwriter.close();
                        }
                        catch (Exception e) {
                            log.warn( "Error during commit: " +  e.getMessage(), e );
                        }
                    }
                }
            }

            public void cancel() {
            }
        };
    }

    
    protected String newUnitOfWorkId() {
        return uuid + Integer.toHexString( count++ );
    }

}
