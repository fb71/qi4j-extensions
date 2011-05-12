/*
 * polymap.org Copyright 2011, Falko Bräutigam, and individual contributors as
 * indicated by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package org.qi4j.entitystore.lucene;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import org.json.JSONArray;
import org.json.JSONException;

import org.apache.commons.collections.list.AbstractListDecorator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import org.qi4j.api.common.QualifiedName;
import org.qi4j.api.common.TypeName;
import org.qi4j.api.entity.EntityReference;
import org.qi4j.api.property.Property;
import org.qi4j.api.property.StateHolder;
import org.qi4j.api.structure.Module;
import org.qi4j.api.value.ValueBuilder;
import org.qi4j.api.value.ValueComposite;
import org.qi4j.runtime.property.PersistentPropertyModel;
import org.qi4j.runtime.types.CollectionType;
import org.qi4j.runtime.types.ValueCompositeType;
import org.qi4j.spi.entity.EntityDescriptor;
import org.qi4j.spi.entity.EntityState;
import org.qi4j.spi.entity.EntityStatus;
import org.qi4j.spi.entity.EntityType;
import org.qi4j.spi.entity.ManyAssociationState;
import org.qi4j.spi.entitystore.EntityStoreException;
import org.qi4j.spi.property.PropertyDescriptor;
import org.qi4j.spi.property.PropertyType;
import org.qi4j.spi.property.PropertyTypeDescriptor;
import org.qi4j.spi.property.ValueType;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

/**
 *
 *
 * @author <a href="http://www.polymap.de">Falko Braeutigam</a>
 */
public class LuceneEntityState
        implements EntityState, Serializable {

    private static final Log log = LogFactory.getLog( LuceneEntityState.class );

    static final String                     PREFIX_PROP = "";
    static final String                     PREFIX_ASSOC = "";
    static final String                     PREFIX_MANYASSOC = "";
    static final String                     SEPARATOR_PROP = "/";

    static final String                     FIELD_MAXX = "_maxx_";
    static final String                     FIELD_MAXY = "_maxy_";
    static final String                     FIELD_MINX = "_minx_";
    static final String                     FIELD_MINY = "_miny_";


    private static final Object             NULL = new Object();

    protected LuceneEntityStoreUnitOfWork   uow;

    protected EntityStatus                  status;

    protected String                        version;

    protected long                          lastModified;

    private final EntityReference           identity;

    private final EntityDescriptor          entityDescriptor;

    protected final Document                doc;

    /** Lazily filled cache of the manyAssociations in use. */
    private Map<QualifiedName,LuceneManyAssociationState> manyAssociations;

    /** Lazily filled cache of the associations in use. */
    private Map<QualifiedName,EntityReference> associations;

    /** Lazily filled cache of the associations in use. Values might be {@link NULL}. */
    private Map<QualifiedName,Object>       properties;


    public LuceneEntityState( LuceneEntityStoreUnitOfWork uow, EntityReference identity,
            EntityDescriptor entityDescriptor ) {
        this( uow, identity, EntityStatus.NEW, entityDescriptor, new Document() );
    }


    public LuceneEntityState( LuceneEntityStoreUnitOfWork uow, EntityReference identity,
            EntityDescriptor entityDescriptor, Document initialState ) {
        this( uow, identity, EntityStatus.LOADED, entityDescriptor, initialState );
    }


    public LuceneEntityState( LuceneEntityStoreUnitOfWork uow,
            EntityReference identity, EntityStatus status,
            EntityDescriptor entityDescriptor, Document state ) {
        this.uow = uow;
        this.identity = identity;
        this.status = status;
        this.entityDescriptor = entityDescriptor;
        this.doc = state;

        version = doc.get( "version" );
        version = version != null ? version : uow.identity();

        String lastModifiedValue = doc.get( "modified" );
        lastModified = lastModifiedValue != null
                ? Long.parseLong( lastModifiedValue )
                : System.currentTimeMillis();
    }


    // EntityState implementation *************************

    public final String version() {
        return version;
    }


    public long lastModified() {
        return lastModified;
    }


    public EntityReference identity() {
        return identity;
    }


    public Object getProperty( QualifiedName stateName ) {
        Object result = properties != null ? properties.get( stateName ) : null;
        if (result == null) {
            PropertyDescriptor propertyDescriptor = entityDescriptor.state()
                    .getPropertyByQualifiedName( stateName );

            if (propertyDescriptor == null) {
                throw new RuntimeException( "No PropertyDescriptor for: " + stateName.name() );
            }
            Type targetType = ((PropertyTypeDescriptor)propertyDescriptor).type();
            ValueType propertyType = ((PropertyTypeDescriptor)propertyDescriptor).propertyType().type();
            String fieldName = PREFIX_PROP + stateName.name();

            result = loadProperty( fieldName, propertyType );

            if (properties == null) {
                properties = new HashMap();
            }
            properties.put( stateName, result );
        }
        return result != NULL ? result : null;
    }


    public void setProperty( QualifiedName stateName, Object newValue ) {
        properties = properties != null ? properties : new HashMap();
        synchronized (properties) {
            properties.put( stateName, newValue != null ? newValue : NULL );
        }

        markUpdated();
    }


    public EntityReference getAssociation( QualifiedName stateName ) {
        String stringValue = doc.get( PREFIX_ASSOC + stateName.name() );
        return stringValue != null
                ? EntityReference.parseEntityReference( stringValue )
                : null;
    }


    public void setAssociation( QualifiedName stateName, EntityReference newEntity ) {
        String stringValue = newEntity == null ? null : newEntity.identity();
        String fieldName = PREFIX_ASSOC + stateName.name();

        storeField( fieldName, stringValue, true, true );

        markUpdated();
    }


    public ManyAssociationState getManyAssociation( QualifiedName stateName ) {
        try {
            manyAssociations = manyAssociations != null ? manyAssociations : new HashMap();
            synchronized (manyAssociations) {
                LuceneManyAssociationState assoc = manyAssociations.get( stateName.name() );
                if (assoc == null) {
                    String fieldName = PREFIX_MANYASSOC + stateName.name();
                    String stringValue = doc.get( fieldName );
                    JSONArray jsonValue = stringValue != null ? new JSONArray( stringValue ) : new JSONArray();
                    assoc = new LuceneManyAssociationState( this, fieldName, jsonValue );
                    manyAssociations.put( stateName, assoc );
                }
                return assoc;
            }
        }
        catch (JSONException e) {
            throw new EntityStoreException( e );
        }
    }


    public void remove() {
        status = EntityStatus.REMOVED;
//        uow.statusChanged( this );
    }


    public EntityStatus status() {
        return status;
    }


    public boolean isOfType( TypeName type ) {
        return entityDescriptor.entityType().type().equals( type );
    }


    public EntityDescriptor entityDescriptor() {
        return entityDescriptor;
    }


    public Document state() {
        return doc;
    }


    public String toString() {
        return identity + "(" + doc.toString() + ")";
    }


    public void hasBeenApplied() {
        status = EntityStatus.LOADED;
        //version = uow.identity();
//        uow.statusChanged( this );
    }


    public void markUpdated() {
        if (status == EntityStatus.LOADED) {
            status = EntityStatus.UPDATED;
        }
//        uow.statusChanged( this );
    }


    protected synchronized Object loadProperty( String fieldName, ValueType propertyType ) {
        // ValueComposite
        if (propertyType instanceof ValueCompositeType) {
            // XXX subTypes are not yet supported
            ValueCompositeType actualValueType = (ValueCompositeType)propertyType;
            List<PropertyType> actualTypes = actualValueType.types();

            final Map<QualifiedName, Object> values = new HashMap<QualifiedName, Object>();
            for (PropertyType actualType : actualTypes) {
                Object value = loadProperty(
                        fieldName + SEPARATOR_PROP + actualType.qualifiedName().name(),
                        actualType.type() );
                if (value != null) {
                    values.put( actualType.qualifiedName(), value );
                }
            }

            try {
                Module module = uow.module();

                ValueBuilder valueBuilder = module.valueBuilderFactory().newValueBuilder(
                        module.classLoader().loadClass( actualValueType.type().name() ) );

                valueBuilder.withState( new StateHolder() {
                    public <T> Property<T> getProperty( Method propertyMethod ) {
                        return null;
                    }
                    public <T> Property<T> getProperty( QualifiedName name ) {
                        return null;
                    }
                    public void visitProperties( StateVisitor visitor ) {
                        for (Map.Entry<QualifiedName, Object> entry : values.entrySet()) {
                            visitor.visitProperty( entry.getKey(), entry.getValue() );
                        }
                    }
                });

                return valueBuilder.newInstance();
            }
            catch (ClassNotFoundException e) {
                throw new IllegalStateException( "Could not deserialize value", e );
            }
        }

        // Collection
        else if (propertyType instanceof CollectionType) {
            String sizeString = doc.get( fieldName + "__length");
            List result = null;
            if (sizeString == null) {
                result = new ArrayList();
            }
            else {
                ValueType collectedType = ((CollectionType)propertyType).collectedType();

                int size = Integer.parseInt( sizeString );

                result = new ArrayList( size );
                for (int i=0; i<size; i++) {
                    Object elm = loadProperty( fieldName + "[" + i + "]", collectedType );
                    result.add( elm );
                }
            }
            // XXX does not work; methods are never called
            return new AbstractListDecorator( result ) {
                public void add( int index, Object object ) {
                    log.debug( "add(): index=" + index + ", object=" + object );
                    markUpdated();
                    getList().add( index, object );
                }
                public boolean addAll( int index, Collection coll ) {
                    markUpdated();
                    return getList().addAll( index, coll );
                }
                public Object remove( int index ) {
                    log.debug( "remove(): index=" + index );
                    markUpdated();
                    return getList().remove( index );
                }
                public Object set( int index, Object object ) {
                    log.debug( "set(): index=" + index + ", object=" + object );
                    markUpdated();
                    return getList().set( index, object );
                }
            };
        }

        // primitive type
        String fieldValue = doc.get( fieldName );
        // save memory
        doc.removeField( fieldName );
        try {
            Class<?> targetType = uow.module().classLoader().loadClass( propertyType.type().name() );
            Object result = fieldValue != null ? ValueCoder.decode( fieldValue, targetType ) : null;
            log.debug( "    loadProperty(): name: " + fieldName + ", value: " + result );
            return result;
        }
        catch (Exception e) {
            log.warn( "Unable to decode property: " + fieldName + ", value=" + fieldValue + ", type=" + propertyType.type(), e );
            return null;
            //            Object defaultValue = DefaultValues.getDefaultValue( module.classLoader()
            //                    .loadClass( propertyType.type().type().name() ) );
            //            values.put( propertyType.qualifiedName(), defaultValue );
        }
    }


    synchronized Document writeBack( String newVersion ) {
        // manyAssociations
        if (manyAssociations != null) {
            for (LuceneManyAssociationState assoc : manyAssociations.values()) {
                JSONArray jsonValue = assoc.writeBack();
                doc.removeField( assoc.getFieldName() );
                if (jsonValue.length() > 0) {
                    doc.add( new Field( assoc.getFieldName(), jsonValue.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED ) );
                }
            }
            manyAssociations = null;
        }
        // properties
        if (properties != null) {
            for (Map.Entry<QualifiedName,Object> entry : properties.entrySet()) {
                PropertyTypeDescriptor propertyDescriptor = entityDescriptor.state()
                        .getPropertyByQualifiedName( entry.getKey() );

                Type type = ((PersistentPropertyModel)propertyDescriptor).type();

                //propertyDescriptor.accessor().getReturnType()
                ValueType propertyType = propertyDescriptor.propertyType().type();
                String fieldName = PREFIX_PROP + entry.getKey().name();

                Object value = entry.getValue();
                storeProperty( fieldName, propertyType, value != NULL ? value : null );
            }
            // save memory; properties are all stored in document and get
            // loaded again lazily
            properties = null;
        }

        // entity header info
        EntityType entityType = entityDescriptor.entityType();
        storeField( "type", entityType.type().name(), true, true );

        version = newVersion;
        storeField( "version", version, true, true );
//        // XXX what is the semantic?
//        if (!newVersion.equals( identity.identity() )) {
//            log.warn( "writeBack(): VERSIONS: " + newVersion + " - " + identity.identity() );
//        }

        lastModified = System.currentTimeMillis();
        storeField( "modified", String.valueOf( lastModified ), true, true );
        return doc;
    }


    protected void storeProperty( String fieldName, ValueType propertyType, Object newValue) {
        // ValueComposite
        if (propertyType instanceof ValueCompositeType) {
            if (newValue == null) {
                // XXX what to do for null?
                return;
            }
            ValueComposite valueComposite = (ValueComposite)newValue;
            StateHolder state = valueComposite.state();
            final Map<QualifiedName, Object> values = new HashMap<QualifiedName, Object>();

            state.visitProperties( new StateHolder.StateVisitor() {
                public void visitProperty( QualifiedName name, Object value ) {
                    values.put( name, value );
                }
            } );

            List<PropertyType> actualTypes = ((ValueCompositeType)propertyType).types();
            if (!newValue.getClass().getInterfaces()[ 0 ].getName().equals(
                    propertyType.type().name() )) {
                throw new RuntimeException( "Actual value is a subtype - use it instead" );
                //                    ValueModel valueModel = (ValueModel)ValueInstance.getValueInstance( (ValueComposite)newValue )
                //                            .compositeModel();
                //
                //                    actualTypes = valueModel.valueType().types();
                //                    json.key( "_type" ).value( valueModel.valueType().type().name() );
            }

            for (PropertyType actualType : actualTypes) {
                storeProperty(
                        fieldName + SEPARATOR_PROP + actualType.qualifiedName().name(),
                        actualType.type(),
                        values.get( actualType.qualifiedName() ) );
            }
            return;
        }

        // Collection
        else if (propertyType instanceof CollectionType) {
            ValueType collectedType = ((CollectionType)propertyType).collectedType();

            int size = 0;
            if (newValue != null) {
                for (Object collectedValue : (Collection)newValue) {
                    storeProperty( fieldName + "[" + size++ + "]", collectedType, collectedValue );
                }
            }
            // ignore removed entries, just update the length field
            storeField( fieldName + "__length", String.valueOf( size ), true, true );
            return;
        }

        // values
        else {

            // Geometry
            if (newValue instanceof Geometry) {
                storeField( fieldName, ValueCoder.encode( newValue, null ), true, true );

                Geometry geom = (Geometry)newValue;
                Envelope envelop = geom.getEnvelopeInternal();
                String maxX = ValueCoder.encode( envelop.getMaxX(), Double.class );
                storeField( fieldName + FIELD_MAXX, maxX, true, false );

                String maxY = ValueCoder.encode( envelop.getMaxY(), Double.class );
                storeField( fieldName + FIELD_MAXY, maxY, true, false );

                String minX = ValueCoder.encode( envelop.getMinX(), Double.class );
                storeField( fieldName + FIELD_MINX, minX, true, false );

                String minY = ValueCoder.encode( envelop.getMinY(), Double.class );
                storeField( fieldName + FIELD_MINY, minY, true, false );
            }

            // primitive values
            else {
                //Class<?> targetType = uow.module().classLoader().loadClass( propertyType.type().name() );
                storeField( fieldName, ValueCoder.encode( newValue, null ), true, true );
            }
        }
    }


    protected synchronized void storeField( String fieldName, String value, boolean singleTerm, boolean store ) {
        Field field = doc.getField( fieldName );
        // remove
        if (field != null && value == null) {
            //log.debug( "   storeField(): remove: " + fieldName );
            doc.removeField( fieldName );
        }
        // set
        else if (field != null && value != null) {
            log.debug( "   storeField(): name: " + fieldName + ", value: " + value );
            field.setValue( value );
        }
        // add
        else if (field == null && value != null) {
            log.debug( "   storeField(): name: " + fieldName + ", value: " + value );
            doc.add( new Field( fieldName, value, Field.Store.YES,
                    singleTerm ? Field.Index.NOT_ANALYZED : Field.Index.ANALYZED ) );
        }
        else {
            //log.debug( "Unhandled: field=" + field + ", newValue=" + stringValue );
        }
    }

}
