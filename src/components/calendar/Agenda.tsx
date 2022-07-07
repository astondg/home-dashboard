import { useEffect, useState } from 'react';
import { findIana } from 'windows-iana';
import { Event } from 'microsoft-graph';
import { useAppContext } from '../../hooks';
import '../../weather-icons.min.css';
import { format, parseISO } from 'date-fns';
import { getUserDayCalendar } from '../../services';

function Agenda() {
  const app = useAppContext();
  const [calEvents, setCalEvents] = useState<Event[]>();
  const dayNames = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"];
  const monthNames = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"];
  const currentDate = new Date();

  useEffect(() => {
    const loadEvents = async() => {
      if (app.user && app.selectedCalendarId && !calEvents) {
        try {
          const ianaTimeZones = findIana(app.user?.timeZone!);
          const events = await getUserDayCalendar(app.authProvider!, app.selectedCalendarId, ianaTimeZones[0].valueOf());
          setCalEvents(events);
        } catch (err: any) {
          app.displayError!(err.message);
        }
      }      
    };

    loadEvents();
  }, [calEvents, app.user, app.selectedCalendarId]);

  return (<div className="flex flex-col">
    <h3 className="text-2xl mb-5 flex flex-row justify-between items-end pl-5 pr-2">
      <div className="text-left">
        <div>{format(currentDate, "eeee")}</div>
        <div className="text-xl">{format(currentDate, "do' of 'MMMM")}</div>
      </div>
      <div>
        <i className="wi wi-showers mr-2 pb-1"></i>        
        <div className="text-xl">8&deg;C - 18&deg;C</div>
      </div>
    </h3>
    
    <div className="grow flex flex-col gap-5">
      {calEvents && calEvents.map(event => {
        const startDate = event.start?.dateTime ? parseISO(event.start.dateTime) : undefined;
        const endDate = event.end?.dateTime ? parseISO(event.end?.dateTime) : undefined;

        return <div className="flex flex-row items-stretch gap-5 text-left pr-2 bg-slate-100/25">
          <div className="border-orange-500 border-r-2"></div>
          <div className="grow">
            <h3 className="font-semibold">{event.subject}</h3>
            <span>{event.location?.displayName}</span>
          </div>
          <div className="font-semibold">
            {startDate && <div>{format(startDate, "kk:mm")}</div>}
            {endDate && <div>{format(endDate, "kk:mm")}</div>}
          </div>
        </div>
      })}
      {(!calEvents || calEvents.length < 1) &&
        <div className="flex flex-row items-stretch gap-5 text-left pr-2">
          <div className="grow pl-5 text-center text-black/50">
            No Events
          </div>
        </div>
      }
    </div>
  </div>);
}

export { Agenda };