/*
 * Copyright 2008 Alin Dreghiciu.
 *
 * Licensed  under the  Apache License,  Version 2.0  (the "License");
 * you may not use  this file  except in  compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.qi4j.index.rdf.query.internal;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.json.JSONException;
import org.json.JSONStringer;
import org.qi4j.api.common.QualifiedName;
import org.qi4j.api.entity.Entity;
import org.qi4j.api.property.StateHolder;
import org.qi4j.api.query.grammar.AssociationIsNullPredicate;
import org.qi4j.api.query.grammar.AssociationNullPredicate;
import org.qi4j.api.query.grammar.BooleanExpression;
import org.qi4j.api.query.grammar.ComparisonPredicate;
import org.qi4j.api.query.grammar.Conjunction;
import org.qi4j.api.query.grammar.ContainsAllPredicate;
import org.qi4j.api.query.grammar.ContainsPredicate;
import org.qi4j.api.query.grammar.Disjunction;
import org.qi4j.api.query.grammar.EqualsPredicate;
import org.qi4j.api.query.grammar.GreaterOrEqualPredicate;
import org.qi4j.api.query.grammar.GreaterThanPredicate;
import org.qi4j.api.query.grammar.LessOrEqualPredicate;
import org.qi4j.api.query.grammar.LessThanPredicate;
import org.qi4j.api.query.grammar.ManyAssociationContainsPredicate;
import org.qi4j.api.query.grammar.MatchesPredicate;
import org.qi4j.api.query.grammar.Negation;
import org.qi4j.api.query.grammar.NotEqualsPredicate;
import org.qi4j.api.query.grammar.OrderBy;
import org.qi4j.api.query.grammar.Predicate;
import org.qi4j.api.query.grammar.PropertyIsNullPredicate;
import org.qi4j.api.query.grammar.PropertyNullPredicate;
import org.qi4j.api.query.grammar.PropertyReference;
import org.qi4j.api.query.grammar.SingleValueExpression;
import org.qi4j.api.query.grammar.ValueExpression;
import org.qi4j.api.value.ValueComposite;
import org.qi4j.index.rdf.query.RdfQueryParser;
import org.qi4j.runtime.types.SerializableType;
import org.qi4j.runtime.types.ValueTypeFactory;
import org.qi4j.spi.property.PropertyType;
import org.qi4j.spi.property.ValueType;
import org.slf4j.LoggerFactory;

import static java.lang.String.*;

/**
 * JAVADOC Add JavaDoc
 */
public class RdfQueryParserImpl
    implements RdfQueryParser
{
    private static ThreadLocal<DateFormat> ISO8601_UTC = new ThreadLocal<DateFormat>()
    {
        @Override
        protected DateFormat initialValue()
        {
            SimpleDateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" );
            dateFormat.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
            return dateFormat;
        }
    };

    private static final Map<Class<? extends Predicate>, String> m_operators;
    private static final Set<Character> reservedChars;

    private Namespaces namespaces = new Namespaces();
    private Triples triples = new Triples( namespaces );

    static
    {
        m_operators = new HashMap<Class<? extends Predicate>, String>();
        m_operators.put( EqualsPredicate.class, "=" );
        m_operators.put( GreaterOrEqualPredicate.class, ">=" );
        m_operators.put( GreaterThanPredicate.class, ">" );
        m_operators.put( LessOrEqualPredicate.class, "<=" );
        m_operators.put( LessThanPredicate.class, "<" );
        m_operators.put( NotEqualsPredicate.class, "!=" );
        m_operators.put( ManyAssociationContainsPredicate.class, "=" );

        reservedChars = new HashSet<Character>( Arrays.asList(
            '\"', '^', '.', '\\', '?', '*', '+', '{', '}', '(', ')', '|', '$', '[', ']'
        ) );
    }

    public String getQuery( final Class<?> resultType,
                            final BooleanExpression whereClause,
                            final OrderBy[] orderBySegments,
                            final Integer firstResult,
                            final Integer maxResults
    )
    {
        // Add type+identity triples last. This makes queries faster since the query engine can reduce the number of triples
        // to check faster
        triples.addDefaultTriples( resultType.getName() );

        // and collect namespaces
        final String filter = processFilter( whereClause, true );
        final String orderBy = processOrderBy( orderBySegments );

        StringBuilder query = new StringBuilder();

        for( String namespace : namespaces.getNamespaces() )
        {
            query.append( format( "PREFIX %s: <%s> %n", namespaces.getNamespacePrefix( namespace ), namespace ) );
        }
        query.append( "SELECT DISTINCT ?identity\n" );
        if( triples.hasTriples() )
        {
            query.append( "WHERE {\n" );
            StringBuilder optional = new StringBuilder();
            for( Triples.Triple triple : triples )
            {
                final String subject = triple.getSubject();
                final String predicate = triple.getPredicate();
                final String value = triple.getValue();

                if( triple.isOptional() )
                {
                    optional.append( format( "OPTIONAL {%s %s %s}. ", subject, predicate, value ) );
                    optional.append( '\n' );
                }
                else
                {
                    query.append( format( "%s %s %s. ", subject, predicate, value ) );
                    query.append( '\n' );
                }
            }

            // Add OPTIONAL statements last
            if (optional.length() > 0)
                query.append( optional.toString() );

            if( filter.length() > 0 )
            {
                query.append( "FILTER " ).append( filter );
            }
            query.append( "\n}" );
        }
        if( orderBy != null )
        {
            query.append( "\nORDER BY " ).append( orderBy );
        }
        if( firstResult != null )
        {
            query.append( "\nOFFSET " ).append( firstResult );
        }
        if( maxResults != null )
        {
            query.append( "\nLIMIT " ).append( maxResults );
        }

        LoggerFactory.getLogger( getClass()).debug( "Query:\n" + query );
        return query.toString();
    }

    private String processFilter( final BooleanExpression expression, boolean allowInline )
    {
        if( expression == null )
        {
            return "";
        }
        if( expression instanceof Conjunction )
        {
            final Conjunction conjunction = (Conjunction) expression;
            String left = processFilter( conjunction.leftSideExpression(), allowInline );
            String right = processFilter( conjunction.rightSideExpression(), allowInline );

            if( left.equals( "" ) )
            {
                return right;
            }
            else if( right.equals( "" ) )
            {
                return left;
            }
            else
            {
                return format( "(%s && %s)", left, right );
            }
        }
        if( expression instanceof Disjunction )
        {
            final Disjunction disjunction = (Disjunction) expression;
            String left = processFilter( disjunction.leftSideExpression(), false );
            String right = processFilter( disjunction.rightSideExpression(), false );
            if( left.equals( "" ) )
            {
                return right;
            }
            else if( right.equals( "" ) )
            {
                return left;
            }
            else
            {
                return format( "(%s || %s)", left, right );
            }
        }
        if( expression instanceof Negation )
        {
            return format( "(!%s)", processFilter( ( (Negation) expression ).expression(), false ) );
        }
        if( expression instanceof MatchesPredicate )
        {
            return processMatchesPredicate( (MatchesPredicate) expression );
        }
        if( expression instanceof ComparisonPredicate )
        {
            return processComparisonPredicate( (ComparisonPredicate) expression, allowInline );
        }
        if( expression instanceof ManyAssociationContainsPredicate )
        {
            return processManyAssociationContainsPredicate( (ManyAssociationContainsPredicate) expression, allowInline );
        }
        if( expression instanceof PropertyNullPredicate )
        {
            return processNullPredicate( (PropertyNullPredicate) expression );
        }
        if( expression instanceof AssociationNullPredicate )
        {
            return processNullPredicate( (AssociationNullPredicate) expression );
        }
        if( expression instanceof ContainsPredicate<?, ?> )
        {
            return processContainsPredicate( (ContainsPredicate<?, ?>) expression );
        }
        if( expression instanceof ContainsAllPredicate<?, ?> )
        {
            return processContainsAllPredicate( (ContainsAllPredicate<?, ?>) expression );
        }
        throw new UnsupportedOperationException( "Expression " + expression + " is not supported" );
    }

    private static String join( String[] strings, String delimiter )
    {
        StringBuilder builder = new StringBuilder();
        for( Integer x = 0; x < strings.length; ++x )
        {
            builder.append( strings[ x ] );
            if( x + 1 < strings.length )
            {
                builder.append( delimiter );
            }
        }

        return builder.toString();
    }

    private String createAndEscapeJSONString( Object value, PropertyReference<?> propertyRef )
        throws JSONException
    {
        ValueType type = ValueTypeFactory.instance().newValueType(
            value.getClass(),
            propertyRef.propertyType(),
            propertyRef.propertyDeclaringType()
        );

        JSONStringer json = new JSONStringer();
        json.array();
        this.createJSONString( value, type, json );
        json.endArray();
        String result = json.toString();
        result = result.substring( 1, result.length() - 1 );

        result = this.escapeJSONString( result );

        return result;
    }

    private String createRegexStringForContaining( String valueVariable, String containedString )
    {
        // The matching value must start with [, then contain something (possibly nothing),
        // then our value, then again something (possibly nothing), and end with ]
        return format( "regex(str(%s), \"^\\\\u005B.*%s.*\\\\u005D$\", \"s\")", valueVariable, containedString );
    }

    private void createJSONString( Object value, ValueType type, JSONStringer stringer )
        throws JSONException
    {
        // TODO the sole purpose of this method is to get rid of "_type" information, which ValueType.toJSON
        // produces for value composites
        // So, change toJSON(...) to be configurable so that the caller can decide whether he wants type
        // information into json string or not
        if( type.isValue() || ( type instanceof SerializableType && value instanceof ValueComposite ) )
        {
            stringer.object();

            // Rest is partial copypasta from ValueCompositeType.toJSON(Object, JSONStringer)

            ValueComposite valueComposite = (ValueComposite) value;
            StateHolder state = valueComposite.state();
            final Map<QualifiedName, Object> values = new HashMap<QualifiedName, Object>();
            state.visitProperties( new StateHolder.StateVisitor<RuntimeException>()
            {
                public void visitProperty( QualifiedName name, Object value )
                {
                    values.put( name, value );
                }
            } );

            List<PropertyType> actualTypes = type.types();
            for( PropertyType propertyType : actualTypes )
            {
                stringer.key( propertyType.qualifiedName().name() );

                Object propertyValue = values.get( propertyType.qualifiedName() );
                if( propertyValue == null )
                {
                    stringer.value( null );
                }
                else
                {
                    this.createJSONString( propertyValue, propertyType.type(), stringer );
                }
            }
            stringer.endObject();
        }
        else
        {
            type.toJSON( value, stringer );
        }
    }

    private String escapeJSONString( String jsonStr )
    {
        StringBuilder builder = new StringBuilder();
        for( Character c : jsonStr.toCharArray() )
        {
            if( reservedChars.contains( c ) )
            {
                builder.append( "\\\\u" ).append( format( "%04X", (int) c ) );
            }
            else
            {
                builder.append( c );
            }
        }

        return builder.toString();
    }

    private String processContainsAllPredicate( final ContainsAllPredicate<?, ?> predicate )
    {
        ValueExpression<?> valueExpression = predicate.valueExpression();
        if( valueExpression instanceof SingleValueExpression<?> )
        {
            String valueVariable = triples.addTriple( predicate.propertyReference(), false ).getValue();
            final SingleValueExpression<?> singleValueExpression = (SingleValueExpression<?>) valueExpression;
            String[] strings = new String[( (Collection<?>) singleValueExpression.value() ).size()];
            Integer x = 0;
            for( Object o : (Collection<?>) singleValueExpression.value() )
            {
                String jsonStr = "";
                if( o != null )
                {
                    try
                    {
                        jsonStr = this.createAndEscapeJSONString( o, predicate.propertyReference() );
                    }
                    catch( JSONException jsone )
                    {
                        throw new UnsupportedOperationException( "Error when JSONing value", jsone );
                    }
                }
                strings[ x ] = this.createRegexStringForContaining( valueVariable, jsonStr );
                x++;
            }
            StringBuilder regex = new StringBuilder();
            if( strings.length > 0 )
            {
                // For some reason, just "FILTER ()" causes error in SPARQL query
                regex.append( "(" );
                regex.append( join( strings, " && " ) );
                regex.append( ")" );
            }
            else
            {
                regex.append( this.createRegexStringForContaining( valueVariable, "" ) );
            }
            return regex.toString();
        }
        else
        {
            throw new UnsupportedOperationException( "Value " + valueExpression + " is not supported." );
        }
    }

    private String processContainsPredicate( final ContainsPredicate<?, ?> predicate )
    {
        ValueExpression<?> valueExpression = predicate.valueExpression();
        if( valueExpression instanceof SingleValueExpression<?> )
        {
            String valueVariable = triples.addTriple( predicate.propertyReference(), false ).getValue();
            SingleValueExpression<?> singleValueExpression = (SingleValueExpression<?>) valueExpression;
            try
            {
                return this.createRegexStringForContaining(
                    valueVariable,
                    this.createAndEscapeJSONString(
                        singleValueExpression.value(),
                        predicate.propertyReference()
                    )
                );
            }
            catch( JSONException jsone )
            {
                throw new UnsupportedOperationException( "Error when JSONing value", jsone );
            }
        }
        else
        {
            throw new UnsupportedOperationException( "Value " + valueExpression + " is not supported." );
        }
    }

    private String processMatchesPredicate( final MatchesPredicate predicate )
    {
        ValueExpression valueExpression = predicate.valueExpression();
        if( valueExpression instanceof SingleValueExpression )
        {
            String valueVariable = triples.addTriple( predicate.propertyReference(), false ).getValue();
            final SingleValueExpression singleValueExpression = (SingleValueExpression) valueExpression;
            return format( "regex(%s,\"%s\")", valueVariable, singleValueExpression.value() );
        }
        else
        {
            throw new UnsupportedOperationException( "Value " + valueExpression + " is not supported" );
        }
    }

    private String processComparisonPredicate( final ComparisonPredicate predicate, boolean allowInline )
    {
        ValueExpression valueExpression = predicate.valueExpression();
        if( valueExpression instanceof SingleValueExpression )
        {
            Triples.Triple triple = triples.addTriple( predicate.propertyReference(), false );

            // Don't use FILTER for equals-comparison. Do direct match instead
            if( predicate instanceof EqualsPredicate && allowInline )
            {
                final SingleValueExpression singleValueExpression = (SingleValueExpression) valueExpression;
                triple.setValue( "\"" + toString( singleValueExpression.value() ) + "\"" );
                return "";
            }
            else
            {
                String valueVariable = triple.getValue();
                final SingleValueExpression singleValueExpression = (SingleValueExpression) valueExpression;
                return String.format( "(%s %s \"%s\")", valueVariable, getOperator( predicate.getClass() ), toString( singleValueExpression.value() ) );
            }
        }
        else
        {
            throw new UnsupportedOperationException( "Value " + valueExpression + " is not supported" );
        }
    }

    private String processManyAssociationContainsPredicate( ManyAssociationContainsPredicate predicate,
                                                            boolean allowInline
    )
    {
        ValueExpression valueExpression = predicate.valueExpression();
        if( valueExpression instanceof SingleValueExpression )
        {
            Triples.Triple triple = triples.addTriple( predicate.associationReference(), false );

            if( allowInline )
            {
                final SingleValueExpression singleValueExpression = (SingleValueExpression) valueExpression;
                triple.setValue( "<" + toString( singleValueExpression.value() ) + ">" );
                return "";
            }
            else
            {
                String valueVariable = triple.getValue();
                final SingleValueExpression singleValueExpression = (SingleValueExpression) valueExpression;
                return String.format( "(%s %s <%s>)", valueVariable, getOperator( predicate.getClass() ), toString( singleValueExpression.value() ) );
            }
        }
        else
        {
            throw new UnsupportedOperationException( "Value " + valueExpression + " is not supported" );
        }
    }

    private String processNullPredicate( final PropertyNullPredicate predicate )
    {
        final String value = triples.addTriple( predicate.propertyReference(), true ).getValue();
        if( predicate instanceof PropertyIsNullPredicate )
        {
            return format( "(! bound(%s))", value );
        }
        else
        {
            return format( "(bound(%s))", value );
        }
    }

    private String processNullPredicate( final AssociationNullPredicate predicate )
    {
        final String value = triples.addTriple( predicate.associationReference(), true ).getValue();
        if( predicate instanceof AssociationIsNullPredicate )
        {
            return format( "(! bound(%s))", value );
        }
        else
        {
            return format( "(bound(%s))", value );
        }
    }

    private String processOrderBy( OrderBy[] orderBySegments )
    {
        if( orderBySegments != null && orderBySegments.length > 0 )
        {
            final StringBuilder orderBy = new StringBuilder();
            for( OrderBy orderBySegment : orderBySegments )
            {
                if( orderBySegment != null )
                {
                    final String valueVariable = triples.addTriple( orderBySegment.propertyReference(), false )
                        .getValue();
                    if( orderBySegment.order() == OrderBy.Order.ASCENDING )
                    {
                        orderBy.append( format( "ASC(%s)", valueVariable ) );
                    }
                    else
                    {
                        orderBy.append( format( "DESC(%s)", valueVariable ) );
                    }
                }
            }
            return orderBy.length() > 0 ? orderBy.toString() : null;
        }
        return null;
    }

    private String getOperator( final Class<? extends Predicate> predicateClass )
    {
        String operator = null;
        for( Map.Entry<Class<? extends Predicate>, String> entry : m_operators.entrySet() )
        {
            if( entry.getKey().isAssignableFrom( predicateClass ) )
            {
                operator = entry.getValue();
                break;
            }
        }
        if( operator == null )
        {
            throw new UnsupportedOperationException( "Predicate [" + predicateClass.getName() + "] is not supported" );
        }
        return operator;
    }

    private String toString( Object value )
    {
        if( value == null )
        {
            return null;
        }

        if( value instanceof Date )
        {
            return ISO8601_UTC.get().format( (Date) value );
        }
        else if( value instanceof Entity )
        {
            return "urn:qi4j:entity:" + value.toString();
        }
        else
        {
            return value.toString();
        }
    }
}