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

import com.facebook.presto.spi.tracing.Tracer;
import com.facebook.presto.spi.tracing.TracerProvider;
import com.facebook.presto.tracing.TracingConfig;
import com.google.inject.Inject;

public class OpenTelemetryTracerProvider
        implements TracerProvider
{
    @Inject
    public OpenTelemetryTracerProvider() {}

    @Override
    public String getName()
    {
        return "Open telemetry tracer provider";
    }

    @Override
    public String getTracerType()
    {
        return TracingConfig.TracerType.OTEL;
    }

    @Override
    public Tracer getNewTracer(String traceToken)
    {
        return new OpenTelemetryTracer(traceToken);
    }
}