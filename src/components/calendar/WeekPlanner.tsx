import { Event as BigCalendarEvent, dateFnsLocalizer } from 'react-big-calendar'
import format from 'date-fns/format'
import parse from 'date-fns/parse'
import startOfWeek from 'date-fns/startOfWeek'
import getDay from 'date-fns/getDay'
import enUS from 'date-fns/locale/en-US'
import React, { useCallback, useEffect, useRef, useState } from 'react'
import { findIana } from 'windows-iana';
import { Event } from 'microsoft-graph';
import { HandwritingRecogniser } from '../../services/handwriting'
import { AppUser, useAppContext } from '../../hooks';
import { createEvent, getUserMonthCalendar, getUserWeekCalendar } from '../../services'
import { parseISO } from 'date-fns/esm'
import { addDays, formatISO, startOfDay } from 'date-fns'

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

function HandwritingDateCellWrapper({ value, onCreateEvent }: { value: Date, onCreateEvent(event: Event): void }) {
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
      canvasRef.current.width = canvasRef.current.clientWidth;
      canvasRef.current.height = canvasRef.current.clientHeight;
      setCanvasContext(canvasRef.current.getContext("2d") || undefined);
    }
  }, [canvasRef.current]);

  const clearCanvas = useCallback(() => {
    if (!canvasRef.current || !canvasContext) return;

    canvasContext.clearRect(0, 0, canvasRef.current.width, canvasRef.current.height);
    setTrace([]);
  }, [canvasRef.current, canvasContext]);

  const handleRecognitionResults = useCallback((results?: string[], error?: Error) => {
    if (!results || results.length < 1) {
      clearCanvas();
      return;
    }

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
    console.log("creating event", newEvent);
    onCreateEvent(newEvent);
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

  const handleCanvasMouseDown = useCallback((event: React.PointerEvent<HTMLCanvasElement>) => {
    if (!canvasRef.current) return;

    // if (event.pointerType === "touch") {
    //   canvasRef.current.setAttribute("style", "display: none;");
    //   const subElement = document.elementFromPoint(event.clientX, event.clientY);
    //   subElement?.dispatchEvent(new globalThis.Event("pointerevent", event));
    //   canvasRef.current.setAttribute("style", "");
    //   return;
    // }

    var rect = canvasRef.current.getBoundingClientRect();
    handleDrawStart(event.clientX, event.clientY, rect.left, rect.top);
  }, [canvasRef.current, handleDrawStart]);

  const handleCanvasMouseMove = useCallback((event: React.PointerEvent<HTMLCanvasElement>) => {
    if (!canvasRef.current) return;

    var rect = canvasRef.current.getBoundingClientRect();
    handleDrawMove(event.clientX, event.clientY, rect.left, rect.top);
  }, [canvasRef.current, handleDrawMove]);

  const handleCanvasMouseUp = useCallback((event: React.PointerEvent<HTMLCanvasElement>) => {
    handleDrawStop();
  }, [handleDrawStart]);

  return (
    <canvas
      ref={canvasRef}
      className="h-full w-full absolute top-0 left-0 bottom-0 right-0 touch-none"
      onPointerDown={handleCanvasMouseDown}
      onPointerMove={handleCanvasMouseMove}
      onPointerUp={handleCanvasMouseUp}
    ></canvas>
  );
}

function WeekPlanner() {
  const app = useAppContext();
  const [calEvents, setCalEvents] = useState<Event[]>();
  const [groupedEvents, setGroupedEvents] = useState<{ name: string, date: Date, events: BigCalendarEvent[] }[]>();

  useEffect(() => {
    const loadEvents = async() => {
      if (app.user && app.selectedCalendarId && !calEvents) {
        try {
          const ianaTimeZones = findIana(app.user?.timeZone!);
          const events = await getUserWeekCalendar(app.authProvider!, app.selectedCalendarId, ianaTimeZones[0].valueOf());
          setCalEvents(events);
        } catch (err: any) {
          app.displayError!(err.message);
        }
      }      
    };

    loadEvents();
  }, [calEvents, app.authProvider, app.user, app.user?.timeZone, app.selectedCalendarId]);

  useEffect(() => {
    const start = startOfWeek(new Date());
    const dayNames = ["sunday", "monday", "tueseday", "wednesday", "thursday", "friday", "saturday"];
    const newWeekEvents: { name: string, date: Date, events: BigCalendarEvent[] }[] = [0,1,2,3,4,5,6].map(day => {
      const groupDate = addDays(start, day);
      return {
        name: format(groupDate, "eeee"),
        date: groupDate,
        events: []
      };
    });

    if (calEvents && calEvents.length > 0) {
      calEvents.forEach(event => {
        if (!event.start?.dateTime) return;
        const startOfEvent = parseISO(event.start.dateTime);
        const existingGroup = newWeekEvents.find(item => item.date.getDate() === startOfDay(startOfEvent).getDate());
        if (!existingGroup) return;
        const newEvent = {
          title: event.subject,
          start: event.start?.dateTime ? parseISO(event.start.dateTime) : undefined,
          end: event.end?.dateTime ? parseISO(event.end.dateTime) : undefined,
          allDay: event.isAllDay?.valueOf()
        };

        existingGroup.events.push(newEvent);
      });
    }

    setGroupedEvents(newWeekEvents.sort((a, b) => a.date.getDate() - b.date.getDate()));
  }, [calEvents]);

  const handleCreateEvent = useCallback(async (event: Event) => {
    if (!app.authProvider || !app.selectedCalendarId) return;
    await createEvent(app.authProvider, app.selectedCalendarId, event);
  }, [app.authProvider, app.selectedCalendarId]);

  return (
    <div className="grid grid-cols-3 gap-4 h-full">
      {groupedEvents && groupedEvents.map(eventGroup => {
        return <div key={eventGroup.name} className="flex flex-col h-full">
          <div className="border-b-2">
            <h3>{eventGroup.name}</h3>
            <span className="text-sm text-gray-500">{format(eventGroup.date, "do' of 'MMMM")}</span>
          </div>
          <div className="grow relative px-1 py-2">
            <HandwritingDateCellWrapper value={eventGroup.date} onCreateEvent={handleCreateEvent} />
            {eventGroup.events && eventGroup.events.map(event => 
              <div className={`${event.allDay ? "bg-blue-500" : "bg-gray-500"} text-white px-1 py-3 my-2`}>
                {event.allDay ? "" : format(event.start!, "kk:mm") + " "}{event.title}
              </div>
            )}
          </div>
        </div>;
      })}
    </div>
  );
}

export { WeekPlanner };