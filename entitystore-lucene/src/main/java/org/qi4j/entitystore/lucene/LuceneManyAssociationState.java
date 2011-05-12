/* 
 * polymap.org
 * Copyright 2009, Polymap GmbH, and individual contributors as indicated
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 * $Id$
 */
package org.qi4j.entitystore.lucene;

import java.util.Iterator;
import java.util.NoSuchElementException;

import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.qi4j.api.entity.EntityReference;
import org.qi4j.spi.entity.ManyAssociationState;
import org.qi4j.spi.entitystore.EntityStoreException;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Braeutigam</a>
 * @version POLYMAP3 ($Revision$)
 * @since 3.0
 */
public class LuceneManyAssociationState
        implements ManyAssociationState, Serializable {

    private static final Log log = LogFactory.getLog( LuceneManyAssociationState.class );

    private LuceneEntityState   entityState;

    private JSONArray           references;
    
    private String              fieldName;


    public LuceneManyAssociationState( LuceneEntityState entityState, String fieldName, JSONArray references ) {
        this.entityState = entityState;
        this.references = references;
        this.fieldName = fieldName;
    }


    JSONArray writeBack() {
        return references;
    }
    
    public String getFieldName() {
        return fieldName;
    }


    public int count() {
        return references.length();
    }


    public boolean contains( EntityReference entityReference ) {
        try {
            for (int i=0; i<references.length(); i++) {
                if (references.get( i ).equals( entityReference.identity() )) {
                    return true;
                }
            }
            return false;
        }
        catch (JSONException e) {
            throw new EntityStoreException( e );
        }
    }


    public boolean add( int idx, EntityReference entityReference ) {
        try {
            if (contains( entityReference )) {
                return false;
            }

            // _p3: insert() is not available
            references.put( idx, entityReference.identity() );
            entityState.markUpdated();
            return true;
        }
        catch (JSONException e) {
            throw new EntityStoreException( e );
        }
    }


    public boolean remove( EntityReference entityReference ) {
        try {
            for (int i = 0; i < references.length(); i++) {
                if (references.get( i ).equals( entityReference.identity() )) {
                    references.remove( i );
                    entityState.markUpdated();
                    return true;
                }
            }
            return false;
        }
        catch (JSONException e) {
            throw new EntityStoreException( e );
        }
    }


    public EntityReference get( int i ) {
        try {
            return new EntityReference( references.getString( i ) );
        }
        catch (JSONException e) {
            throw new EntityStoreException( e );
        }
    }


    public Iterator<EntityReference> iterator() {
        
        return new Iterator<EntityReference>() {

            int index = 0;

            public boolean hasNext() {
                return index < references.length();
            }

            public EntityReference next() {
                try {
                    EntityReference ref = new EntityReference( references.getString( index ) );
                    index++;
                    return ref;
                }
                catch (JSONException e) {
                    throw new NoSuchElementException();
                }
            }

            public void remove() {
                throw new UnsupportedOperationException( "Use ManyAssociation.remove() instead." );
            }
        };
    }

}
