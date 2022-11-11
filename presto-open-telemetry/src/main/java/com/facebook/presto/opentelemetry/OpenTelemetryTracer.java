/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.opentelemetry;

import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.tracing.Tracer;
import com.facebook.presto.tracing.TracingConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.facebook.presto.spi.StandardErrorCode.DISTRIBUTED_TRACING_ERROR;

public class OpenTelemetryTracer
        implements Tracer
{
    private static String currentContextPropagator = TracingConfig.ContextPropagator.B3_SINGLE_HEADER;
    private static OpenTelemetry openTelemetry = OpenTelemetryBuilder.build(currentContextPropagator);
    private final io.opentelemetry.api.trace.Tracer openTelemetryTracer;
    private final String traceToken;
    private final Span parentSpan;

    public final Map<String, Span> spanMap = new ConcurrentHashMap<String, Span>();
    public final Map<String, Span> recorderSpanMap = new LinkedHashMap<String, Span>();

    // Trivial getter method to return carrier
    // Carrier will be the propagated context string
    private final TextMapGetter<String> trivialGetter = new TextMapGetter<String>()
    {
        @Override
        public String get(String carrier, String key)
        {
            return carrier;
        }

        @Override
        public Iterable<String> keys(String carrier)
        {
            return Arrays.asList(get(carrier, null));
        }
    };

    public OpenTelemetryTracer(String traceToken, String contextPropagator, String propagatedContext)
    {
        // Rebuild OPEN_TELEMETRY instance if necessary (to use different context propagator)
        // Will only occur once at max, if contextPropagator is different from B3_SINGLE_HEADER
        if (!contextPropagator.equals(currentContextPropagator)) {
            openTelemetry = OpenTelemetryBuilder.build(contextPropagator);
            currentContextPropagator = contextPropagator;
        }

        openTelemetryTracer = openTelemetry.getTracer(tracerName);
        this.traceToken = traceToken;

        if (propagatedContext == null) {
            this.parentSpan = createParentSpan();
        }
        else {
            Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
                    .extract(Context.current(), propagatedContext, trivialGetter);
            try (Scope scope = extractedContext.makeCurrent()) {
                this.parentSpan = createParentSpan();
            }
        }

        synchronized (recorderSpanMap) {
            recorderSpanMap.put("Trace start", this.parentSpan);
        }
    }

    private Span createParentSpan()
    {
        Span parentSpan = openTelemetryTracer.spanBuilder("Trace start").startSpan();
        parentSpan.setAttribute("trace_id", traceToken);
        return parentSpan;
    }

    /**
     * Add annotation as event to parent span
     * @param annotation event to add
     */
    @Override
    public void addPoint(String annotation)
    {
        parentSpan.addEvent(annotation);
    }

    /**
     * Create new span with Open Telemetry tracer
     * @param blockName name of span
     * @param annotation event to add to span
     */

    @Override
    public void startBlock(String blockName, String annotation)
    {
        if (spanMap.containsKey(blockName)) {
            throw new PrestoException(DISTRIBUTED_TRACING_ERROR, "Duplicated block inserted: " + blockName);
        }
        Span span = openTelemetryTracer.spanBuilder(blockName)
                .setParent(Context.current().with(parentSpan))
                .startSpan();
        span.addEvent(annotation);
        spanMap.put(blockName, span);
        synchronized (recorderSpanMap) {
            recorderSpanMap.put(blockName, span);
        }
    }

    @Override
    public void addPointToBlock(String blockName, String annotation)
    {
        if (!spanMap.containsKey(blockName)) {
            throw new PrestoException(DISTRIBUTED_TRACING_ERROR, "Adding point to non-existing block: " + blockName);
        }
        spanMap.get(blockName).addEvent(annotation);
    }

    /**
     * End Open Telemetry span
     * @param blockName name of span
     * @param annotation event to add to span
     */
    @Override
    public void endBlock(String blockName, String annotation)
    {
        if (!spanMap.containsKey(blockName)) {
            throw new PrestoException(DISTRIBUTED_TRACING_ERROR, "Trying to end a non-existing block: " + blockName);
        }
        spanMap.remove(blockName);
        synchronized (recorderSpanMap) {
            Span span = recorderSpanMap.get(blockName);
            span.addEvent(annotation);
            span.end();
        }
    }

    @Override
    public void endTrace(String annotation)
    {
        parentSpan.addEvent(annotation);
        parentSpan.end();
    }

    @Override
    public String getTracerId()
    {
        if (traceToken != null) {
            return traceToken;
        }
        return tracerName;
    }
}
