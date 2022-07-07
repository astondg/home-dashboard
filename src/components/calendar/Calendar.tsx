import { Calendar as BigCalendar, Event as BigCalendarEvent, dateFnsLocalizer } from 'react-big-calendar'
import format from 'date-fns/format'
import parse from 'date-fns/parse'
import startOfWeek from 'date-fns/startOfWeek'
import getDay from 'date-fns/getDay'
import enUS from 'date-fns/locale/en-US'
import React, { useCallback, useEffect, useRef, useState } from 'react'
import { findIana } from 'windows-iana';
import { Event } from 'microsoft-graph';
import { HandwritingRecogniser } from '../../services/handwriting'
import { useAppContext } from '../../hooks';
import { getUserMonthCalendar } from '../../services'
import { parseISO } from 'date-fns/esm'
import { formatISO } from 'date-fns'

const locales = {
  'en-US': enUS,
};

const localizer = dateFnsLocalizer({
  format,
  parse,
  startOfWeek,
  getDay,
  locales,
});

function HandwritingDateCellWrapper({ value }: { value?: Date }) {
  const [currentLineWidth, setCurrentLineWidth] = useState<number>(3);
  const [handwritingX, setHandwritingX] = useState<number[]>([]);
  const [handwritingY, setHandwritingY] = useState<number[]>([]);
  const [isDrawing, setIsDrawing] = useState(false);
  const [canvasContext, setCanvasContext] = useState<CanvasRenderingContext2D>();
  const [trace, setTrace] = useState<number[][][]>([]);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [recogniserInterval, setRecogniserInterval] = useState<number>();

  useEffect(() => {
    if (!canvasRef.current) {
      setCanvasContext(undefined);
    } else {
      setCanvasContext(canvasRef.current.getContext("2d") || undefined);
    }
  }, [canvasRef.current]);

  const clearCanvas = useCallback(() => {
    if (!canvasRef.current || !canvasContext) return;

    canvasContext.clearRect(0, 0, canvasRef.current.width, canvasRef.current.height);
    setTrace([]);
  }, [canvasRef.current, canvasContext]);

  const handleRecognitionResults = useCallback((results?: string[], error?: Error) => {
    if (!value) return;
    if (!results || results.length < 1) return;

    const newEvent: Event = {
      subject: results[0],
      start: {
        dateTime: formatISO(value),
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
      },
      end: {
        dateTime: formatISO(value),
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
      },
      isAllDay: true
    }
    clearCanvas();
  }, [clearCanvas]);

  const sendRecogniserRequest = useCallback(() => {
    const newInterval = setInterval(() => {
      HandwritingRecogniser(
        trace,
        {
          width: canvasRef.current?.width,
          height: canvasRef.current?.height,
          language: "en-AU"
        },
        handleRecognitionResults
      );

      if (newInterval) {
        clearInterval(newInterval);
        setRecogniserInterval(undefined);
      }
    },
    2000);
    return newInterval;
  }, [trace]);

  const handleDrawStart = useCallback((clientX: number, clientY: number, boundsLeft: number, boundsTop: number) => {
    if (!canvasContext) return;

    if (recogniserInterval) {
      clearInterval(recogniserInterval);
      setRecogniserInterval(undefined);
    }

    canvasContext.lineWidth = currentLineWidth;
    setIsDrawing(true);
    canvasContext.beginPath();
    var x = clientX - boundsLeft;
    var y = clientY - boundsTop;
    canvasContext.moveTo(x, y);
    setHandwritingX([x]);
    setHandwritingY([y]);
  }, [canvasContext, recogniserInterval]);

  const handleDrawMove = useCallback((clientX: number, clientY: number, boundsLeft: number, boundsTop: number) => {
    if (!canvasContext || !isDrawing) return;

    var x = clientX - boundsLeft;
    var y = clientY - boundsTop;
    canvasContext.lineTo(x, y);
    canvasContext.stroke();
    setHandwritingX(current => [...current, x]);
    setHandwritingY(current => [...current, y]);
  }, [canvasContext, isDrawing]);

  const handleDrawStop = useCallback(() => {
    if (!canvasContext) return;

    var newTrace = [
      handwritingX,
      handwritingY,
      []
    ];
    setTrace(current => [...current, newTrace]);
    setIsDrawing(false);
    const newInterval = sendRecogniserRequest();
    setRecogniserInterval(newInterval);
  }, [canvasContext, trace]);

  const handleCanvasMouseDown = useCallback((event: React.MouseEvent<HTMLCanvasElement>) => {
    if (!canvasRef.current) return;

    var rect = canvasRef.current.getBoundingClientRect();
    handleDrawStart(event.clientX, event.clientY, rect.left, rect.top);
  }, [canvasRef.current, handleDrawStart]);

  const handleCanvasMouseMove = useCallback((event: React.MouseEvent<HTMLCanvasElement>) => {
    if (!canvasRef.current) return;

    var rect = canvasRef.current.getBoundingClientRect();
    handleDrawMove(event.clientX, event.clientY, rect.left, rect.top);
  }, [canvasRef.current, handleDrawMove]);

  const handleCanvasMouseUp = useCallback((event: React.MouseEvent<HTMLCanvasElement>) => {
    handleDrawStop();
  }, [handleDrawStart]);

  const handleCanvasTouchStart = useCallback((event: React.TouchEvent<HTMLCanvasElement>) => {
    if (!canvasRef.current) return;

    var de = document.documentElement;
    var box = canvasRef.current.getBoundingClientRect();
    var top = box.top + window.pageYOffset - de.clientTop;
    var left = box.left + window.pageXOffset - de.clientLeft;
    var touch = event.changedTouches[0];
    handleDrawStart(touch.pageX, touch.pageY, left, top);
  }, [canvasRef.current, handleDrawStart]);

  const handleCanvasTouchMove = useCallback((event: React.TouchEvent<HTMLCanvasElement>) => {
    if (!canvasRef.current) return;

    var de = document.documentElement;
    var box = canvasRef.current.getBoundingClientRect();
    var top = box.top + window.pageYOffset - de.clientTop;
    var left = box.left + window.pageXOffset - de.clientLeft;
    var touch = event.changedTouches[0];
    handleDrawMove(touch.pageX, touch.pageY, left, top);
  }, [canvasRef.current, handleDrawMove])

  const handleCanvasTouchEnd = useCallback((event: React.TouchEvent<HTMLCanvasElement>) => {
    handleDrawStop();
  }, [handleDrawStop]);

  return (
    <div className="calendar-canvas">
      <canvas
        ref={canvasRef}
        onMouseDown={handleCanvasMouseDown}
        onMouseMove={handleCanvasMouseMove}
        onMouseUp={handleCanvasMouseUp}
        onTouchStart={handleCanvasTouchStart}
        onTouchMove={handleCanvasTouchMove}
        onTouchEnd={handleCanvasTouchEnd}
      ></canvas>
    </div>
  );
}

function Calendar() {
  const app = useAppContext();
  const [events, setEvents] = useState<BigCalendarEvent[]>();

  useEffect(() => {
    const loadEvents = async() => {
      if (app.user && app.selectedCalendarId && !events) {
        try {
          const ianaTimeZones = findIana(app.user?.timeZone!);
          const events = await getUserMonthCalendar(app.authProvider!, app.selectedCalendarId, ianaTimeZones[0].valueOf());
          setEvents(events.map(event => ({
            title: event.subject,
            start: event.start?.dateTime ? parseISO(event.start.dateTime) : undefined,
            end: event.end?.dateTime ? parseISO(event.end.dateTime) : undefined,
            allDay: event.isAllDay?.valueOf()
          })));
        } catch (err: any) {
          app.displayError!(err.message);
        }
      }
    };

    loadEvents();
  }, [app.authProvider, app.selectedCalendarId, app.user, app.user?.timeZone, events]);

  return (
    <BigCalendar
      components={{ dateCellWrapper: HandwritingDateCellWrapper }}
      localizer={localizer}
      startAccessor="start"
      endAccessor="end"
      events={events}
      style={{ height: "100%" }}
    />
  );
}

export { Calendar };