/* 
 * polymap.org
 * Copyright 2011, Falko Bräutigam, and individual contributors as 
 * indicated by the @authors tag.
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

import java.util.Date;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.geotools.geojson.geom.GeometryJSON;

import org.apache.lucene.util.NumericUtils;

import com.vividsolutions.jts.geom.Geometry;

/**
 * Encode/decode primitive types into/from string representation that can be
 * stored and searched in the Lucene index.
 * 
 * @author <a href="http://www.polymap.de">Falko Braeutigam</a>
 * @since 3.1
 */
class ValueCoder {

    static final GeometryJSON           jsonCoder = new GeometryJSON( 6 );
    
    /**
     * 
     * @param value
     * @param targetType Optional value that specifies which type is to be used
     *        for the encode.
     * @param name The name of the field, or null.
     * @param doc The document to encode into, or null,
     */
    public static String encode( Object value, Class targetType ) {
        if (value == null) {
            return null;
        }
        
        Class type = targetType != null ? targetType : value.getClass();
        
        // Geometry
        if (Geometry.class.isAssignableFrom( type )) {
            try {
                Geometry geom = (Geometry)value;
                StringWriter out = new StringWriter( 1024 );
                jsonCoder.write( geom, out );
                return out.toString();
            }
            catch (IOException e) {
                throw new RuntimeException( e );
            }
        }
        // Numbers 32bit
        else if (Integer.class.isAssignableFrom( type )) {
            return NumericUtils.intToPrefixCoded( ((Integer)value).intValue() );
        }
        else if (Byte.class.isAssignableFrom( type )) {
            return NumericUtils.intToPrefixCoded( ((Byte)value).intValue() );
        }
        else if (Short.class.isAssignableFrom( type )) {
            return NumericUtils.intToPrefixCoded( ((Short)value).intValue() );
        }
        else if (Float.class.isAssignableFrom( type )) {
            return NumericUtils.floatToPrefixCoded( ((Float)value).floatValue() );
        }
        // Numbers 64bit
        else if (Long.class.isAssignableFrom( type )) {
            return NumericUtils.longToPrefixCoded( ((Long)value).longValue() );
        }
        else if (Double.class.isAssignableFrom( type )) {
            return NumericUtils.doubleToPrefixCoded( ((Double)value).doubleValue() );
        }
        // Date
        else if (Date.class.isAssignableFrom( type )) {
            return String.valueOf( ((Date)value).getTime() );
        }
        // Boolean
        else if (Boolean.class.isAssignableFrom( type )) {
            return ((Boolean)value).toString();
        }
        // String
        else if (String.class.isAssignableFrom( type )) {
            return (String)value;
        }
        else {
            throw new RuntimeException( "Unknown value type: " + type );
        }
    }
    

    /**
     *
     * @param encoded
     * @param targetType Optional value that specifies which type is to be used
     *        for the encode.
     */
    public static Object decode( String encoded, Class targetType ) {
        // Geometry
        if (Geometry.class.isAssignableFrom( targetType )) {
            try {
                return jsonCoder.read( new StringReader( encoded ) );
            }
            catch (IOException e) {
                throw new RuntimeException( e );
            }
        }
        // Numbers
        else if (Integer.class.isAssignableFrom( targetType ) ) {
            return Integer.valueOf( NumericUtils.prefixCodedToInt( encoded ) );
        }
        else if (Short.class.isAssignableFrom( targetType ) ) {
            return new Short( (short)NumericUtils.prefixCodedToInt( encoded ) );
        }
        else if (Byte.class.isAssignableFrom( targetType ) ) {
            return new Byte( (byte)NumericUtils.prefixCodedToInt( encoded ) );
        }
        else if (Float.class.isAssignableFrom( targetType ) ) {
            return new Float( NumericUtils.prefixCodedToFloat( encoded ) );
        }
        else if (Long.class.isAssignableFrom( targetType) ) {
            return new Long( NumericUtils.prefixCodedToLong( encoded ) );
        }
        else if (Double.class.isAssignableFrom( targetType) ) {
            return new Double( NumericUtils.prefixCodedToDouble( encoded ) );
        }
        // Date
        else if (Date.class.isAssignableFrom( targetType )) {
            return new Date( Long.parseLong( encoded ) );
        }
        // Boolean
        else if (Boolean.class.isAssignableFrom( targetType )) {
            return Boolean.valueOf( encoded );
        }
        // String
        else if (String.class.isAssignableFrom( targetType )) {
            return encoded;
        }
        else {
            throw new RuntimeException( "Unknown value type: " + targetType );
        }
        
    }
    
}
