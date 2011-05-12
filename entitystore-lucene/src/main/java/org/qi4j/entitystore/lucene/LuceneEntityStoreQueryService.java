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
 *
 * $Id$
 */
package org.qi4j.entitystore.lucene;

import java.util.Iterator;
import java.util.NoSuchElementException;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import org.qi4j.api.entity.EntityReference;
import org.qi4j.api.injection.scope.Service;
import org.qi4j.api.mixin.Mixins;
import org.qi4j.api.query.grammar.BooleanExpression;
import org.qi4j.api.query.grammar.OrderBy;
import org.qi4j.api.service.ServiceComposite;
import org.qi4j.spi.query.EntityFinder;
import org.qi4j.spi.query.EntityFinderException;

/**
 * This query service relies on the store directly, which is a Lucene by itself. 
 * 
 * @author <a href="http://www.polymap.de">Falko Braeutigam</a>
 * @version POLYMAP3 ($Revision$)
 * @since 3.0
 */
@Mixins({ 
        LuceneEntityStoreQueryService.LuceneEntityFinderMixin.class,
})
public interface LuceneEntityStoreQueryService
        extends EntityFinder, ServiceComposite {

    /**
     * 
     */
    public static class LuceneEntityFinderMixin
            implements EntityFinder {

        private static Log log = LogFactory.getLog( LuceneQueryParserImpl.class );

        @Service
        private LuceneEntityStoreService    entityStoreService;
        
        private LuceneQueryParserImpl       queryParser = new LuceneQueryParserImpl();

        private IdentityFieldSelector       identityFieldSelector = new IdentityFieldSelector();

        
        public Iterable<EntityReference> findEntities( 
                String resultType,
                BooleanExpression whereClause, 
                OrderBy[] orderBySegments, 
                Integer firstResult,
                Integer maxResults )
                throws EntityFinderException {

            try {
                long start = System.currentTimeMillis();
                log.debug( "findEntities(): resultType=" + resultType + ", where=" + whereClause + ", maxResults=" + maxResults );
                if (firstResult != null && firstResult.intValue() != 0) {
                    throw new UnsupportedOperationException( "Not implemented yet: firstResult != 0" );
                }
                
                // build Lucene query
                Query query = queryParser.createQuery( resultType, whereClause, orderBySegments );

                // execute Lucene query
                final IndexSearcher searcher = entityStoreService.getIndexSearcher();
                TopDocs topDocs = searcher.search( query, maxResults != null ? maxResults : Integer.MAX_VALUE );
                final ScoreDoc[] scoreDocs = topDocs.scoreDocs;
                log.debug( "    results: " + scoreDocs.length + " (" + (System.currentTimeMillis()-start) + "ms)" );
                
                return new Iterable<EntityReference>() {

                    public Iterator<EntityReference> iterator() {
                        
                        return new Iterator<EntityReference>() {
                    
                            private int     index = 0;

                            public boolean hasNext() {
                                return index < scoreDocs.length;
                            }

                            public EntityReference next() {
                                if (hasNext()) {
                                    try {
                                        Document doc = searcher.doc( scoreDocs[index++].doc, identityFieldSelector );
                                        return new EntityReference( doc.get( "identity" ) );
                                    }
                                    catch (Exception e) {
                                        throw new RuntimeException( e );
                                    }
                                }
                                else {
                                    throw new NoSuchElementException( "Query result count: " + scoreDocs.length );
                                }
                            }

                            public void remove() {
                                throw new UnsupportedOperationException( "Removing entities from query result is not defined.");
                            }
                            
                        };
                    }
                };
            }
            catch (Exception e) {
                throw new EntityFinderException( e );
            }
        }


        public EntityReference findEntity( 
                String resultType, BooleanExpression whereClause )
                throws EntityFinderException {
            
            try {
                IndexSearcher searcher = entityStoreService.getIndexSearcher();
                Query query = queryParser.createQuery( resultType, whereClause, null );
                TopDocs topDocs = searcher.search( query, 1 );
                ScoreDoc[] scoreDocs = topDocs.scoreDocs;
                
                if (scoreDocs.length > 0) { 
                    Document doc = searcher.doc( scoreDocs[0].doc, identityFieldSelector );
                    return new EntityReference( doc.get( "identity" ) );
                }
                else {
                    return null;
                }
            }
            catch (Exception e) {
                throw new EntityFinderException( e );
            }
        }

        
        /**
         * 
         */
        class IdentityFieldSelector
                implements FieldSelector {

            public FieldSelectorResult accept( String fieldName ) {
                return fieldName.equals( "identity" ) 
                        ? FieldSelectorResult.LOAD : FieldSelectorResult.NO_LOAD;
            }
            
        }

        
        public long countEntities( String resultType, BooleanExpression whereClause )
        throws EntityFinderException {
            log.debug( "countEntities(): resultType=" + resultType + ", where=" + whereClause );

            // Lucene does not like Integer.MAX_VALUE
            int maxResults = 10000000;
            try {
                long start = System.currentTimeMillis();
                
                Query query = queryParser.createQuery( resultType, whereClause, null );
                // XXX cache this result for subsequent findEntity() calls
                TopDocs topDocs = entityStoreService.getIndexSearcher().search( query, maxResults );
                
//                ScoreDoc[] scoreDocs = topDocs.scoreDocs;
//                int result = scoreDocs.length;
                
                int result = topDocs.totalHits;
                
                log.debug( "    results: " + result + " (" + (System.currentTimeMillis()-start) + "ms)" );
                return result;
            }
            catch (IOException e) {
                throw new EntityFinderException( e );
            }
        }
    }

}
