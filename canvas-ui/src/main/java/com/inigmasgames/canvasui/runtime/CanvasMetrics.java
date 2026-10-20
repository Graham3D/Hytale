package com.inigmasgames.canvasui.runtime;

public record CanvasMetrics(long pointerEvents, long uiUpdates, long pageRebuilds,
                            long commandsEmitted, double pointerEventsPerSecond,
                            double uiUpdatesPerSecond, double averageProcessingLatencyMillis,
                            double peakProcessingLatencyMillis) { }
