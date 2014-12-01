/*
*  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.siddhi.core.stream.output;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.wso2.siddhi.core.config.SiddhiContext;
import org.wso2.siddhi.core.event.Event;
import org.wso2.siddhi.core.event.EventFactory;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.exception.QueryCreationException;
import org.wso2.siddhi.core.stream.StreamJunction;
import org.wso2.siddhi.core.util.SiddhiConstants;
import org.wso2.siddhi.query.api.annotation.Element;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.exception.DuplicateAnnotationException;
import org.wso2.siddhi.query.api.util.AnnotationHelper;

import java.util.ArrayList;
import java.util.List;

public abstract class StreamCallback implements StreamJunction.Receiver {

    private String streamId;
    private AbstractDefinition streamDefinition;
    private List<Event> eventBuffer = new ArrayList<Event>();
    private SiddhiContext siddhiContext;
    private AsyncEventHandler asyncEventHandler;

    private Disruptor<Event> disruptor;
    private RingBuffer<Event> ringBuffer;


    @Override
    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public void setStreamDefinition(AbstractDefinition streamDefinition) {
        this.streamDefinition = streamDefinition;
    }

    public void setContext(SiddhiContext siddhiContext) {
        this.siddhiContext = siddhiContext;
    }

    @Override
    public void receive(StreamEvent streamEvent) {
        while (streamEvent != null){
            if (disruptor == null) {
                receive(new Event[]{new Event(streamEvent.getOutputData().length).copyFrom(streamEvent)});
            } else {
                receiveAsync(new Event(streamEvent.getOutputData().length).copyFrom(streamEvent));
            }
            streamEvent = streamEvent.getNext();
        }
    }

    @Override
    public void receive(Event event) {
        if (disruptor == null) {
            receive(new Event[]{event});
        } else {
            receiveAsync(event);
        }
    }

    @Override
    public void receive(Event event, boolean endOfBatch) {
        eventBuffer.add(event);
        if (endOfBatch) {
            receive(eventBuffer.toArray(new Event[eventBuffer.size()]));
            eventBuffer.clear();
        }
    }

    public void receive(long timeStamp, Object[] data) {
        if (disruptor == null) {
            receive(new Event[]{new Event(timeStamp, data)});
        } else {
            receiveAsync(new Event(timeStamp, data));
        }
    }

    public abstract void receive(Event[] events);

    private void receiveAsync(Event event) {
        long sequenceNo = ringBuffer.next();
        try {
            Event existingEvent = ringBuffer.get(sequenceNo);
            existingEvent.setTimestamp(event.getTimestamp());
            existingEvent.setData(event.getData());
        } finally {
            ringBuffer.publish(sequenceNo);
        }
    }

    public synchronized void startProcessing() {

        Boolean asyncEnabled = null;
        try {
            Element element = AnnotationHelper.getAnnotationElement(SiddhiConstants.ANNOTATION_CONFIG,
                    SiddhiConstants.ANNOTATION_ELEMENT_ASYNC,
                    streamDefinition.getAnnotations());

            if (element != null) {
                asyncEnabled = SiddhiConstants.TRUE.equalsIgnoreCase(element.getValue());
            }

        } catch (DuplicateAnnotationException e) {
            throw new QueryCreationException(e.getMessage() + " for the same Stream Definition " +
                    streamDefinition.toString());
        }

        if (asyncEnabled != null && asyncEnabled || asyncEnabled == null) {

            disruptor = new Disruptor<Event>(new EventFactory(streamDefinition.getAttributeList().size()), siddhiContext.getDefaultEventBufferSize(),
                    siddhiContext.getExecutorService(), ProducerType.SINGLE, new SleepingWaitStrategy());

            asyncEventHandler = new AsyncEventHandler(this);
            disruptor.handleEventsWith(asyncEventHandler);
            ringBuffer = disruptor.start();
        }
    }

    public synchronized void stopProcessing() {
        if (disruptor != null) {
            asyncEventHandler.streamCallback = null;
            disruptor.shutdown();
        }
    }

    public class AsyncEventHandler implements EventHandler<Event> {

        private StreamCallback streamCallback;

        public AsyncEventHandler(StreamCallback streamCallback) {
            this.streamCallback = streamCallback;
        }

        /**
         * Called when a publisher has published an event to the {@link com.lmax.disruptor.RingBuffer}
         *
         * @param event published to the {@link com.lmax.disruptor.RingBuffer}
         * @param sequence    of the event being processed
         * @param endOfBatch  flag to indicate if this is the last event in a batch from the {@link com.lmax.disruptor.RingBuffer}
         * @throws Exception if the EventHandler would like the exception handled further up the chain.
         */
        @Override
        public void onEvent(Event event, long sequence, boolean endOfBatch) throws Exception {
            if (streamCallback != null) {
                streamCallback.receive(event, endOfBatch);
            }
        }
    }


}
