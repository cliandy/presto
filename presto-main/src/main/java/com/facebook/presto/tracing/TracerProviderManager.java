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
package com.facebook.presto.tracing;

import com.facebook.presto.spi.tracing.TracerProvider;
import com.google.inject.Inject;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class TracerProviderManager
{
    private final String tracerType;
    private final boolean enableDistributedTracing;
    private final TracingConfig.DistributedTracingMode distributedTracingMode;
    private final boolean systemTracingEnabled;
    private TracerProvider tracerProvider;

    @Inject
    public TracerProviderManager(TracingConfig config)
    {
        requireNonNull(config, "config is null");

        this.tracerType = config.getTracerType();
        this.enableDistributedTracing = config.getEnableDistributedTracing();
        this.distributedTracingMode = config.getDistributedTracingMode();
        this.systemTracingEnabled = this.enableDistributedTracing && this.distributedTracingMode.name().equalsIgnoreCase(TracingConfig.DistributedTracingMode.ALWAYS_TRACE.name());
    }

    public void addTracerProviderFactory(TracerProvider tracerProvider)
    {
        if (!tracerType.equals(tracerProvider.getTracerType())) {
            throw new IllegalArgumentException(
                    format(
                            "Plugin-configured tracer provider ('%s') does not match system-configured provider ('%s').",
                            this.tracerProvider.getName(),
                            tracerType));
        }
        if (systemTracingEnabled) {
            if (this.tracerProvider != null) {
                throw new IllegalArgumentException(format("Only a single plugin should set the tracer provider ('%s').", this.tracerProvider.getTracerType()));
            }
            this.tracerProvider = tracerProvider;
        }
    }

    public void loadTracerProvider()
    {
        if (this.tracerProvider == null) {
            // open-telemetry plugin not used / tracer provider not specified or not matching system config / tracing disabled
            // Check if SimpleTracer is configured and tracing is enabled
            // Otherwise, use Noop implementation
            if (this.tracerType.equals(TracingConfig.TracerType.SIMPLE) && systemTracingEnabled) {
                this.tracerProvider = new SimpleTracerProvider();
            }
            else {
                this.tracerProvider = new NoopTracerProvider();
            }
        }
    }

    public TracerProvider getTracerProvider()
    {
        return this.tracerProvider;
    }
}
