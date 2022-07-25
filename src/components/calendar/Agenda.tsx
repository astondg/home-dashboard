import { useEffect, useMemo, useState } from 'react';
import { findIana } from 'windows-iana';
import { Event } from 'microsoft-graph';
import { useAppContext } from '../../hooks';
import '../../weather-icons.min.css';
import { format, parseISO } from 'date-fns';
import { getUserDayCalendar } from '../../services';

function Agenda() {
  const app = useAppContext();
  const [calEvents, setCalEvents] = useState<Event[]>();
  const currentDate = useMemo(() => app.currentDay || new Date(), [app.currentDay]);

  useEffect(() => {
    const loadEvents = async() => {
      if (app.user && app.selectedCalendar?.id && app.currentDay && !calEvents) {
        try {
          const ianaTimeZones = findIana(app.user?.timeZone!);
          const events = await getUserDayCalendar(app.authProvider!, app.selectedCalendar.id, app.currentDay, ianaTimeZones[0].valueOf());
          setCalEvents(events);
        } catch (err: any) {
          app.displayError!(err.message);
        }
      }      
    };

    loadEvents();
  }, [calEvents, app.user, app.selectedCalendar, app.currentDay]);

  return (<div className="flex flex-col">
    <h3 className="flex flex-row items-end justify-between pl-5 pr-2 mb-5 text-2xl">
      <div className="text-left">
        <div>{format(currentDate, "eeee")}</div>
        <div className="text-xl">{format(currentDate, "do' of 'MMMM")}</div>
      </div>
      <div>
        <i className="pb-1 mr-2 wi wi-showers"></i>        
        <div className="text-xl">8&deg;C - 18&deg;C</div>
      </div>
    </h3>
    
    <div className="flex flex-col gap-5 grow">
      {calEvents && calEvents.map(event => {
        const startDate = event.start?.dateTime ? parseISO(event.start.dateTime) : undefined;
        const endDate = event.end?.dateTime ? parseISO(event.end?.dateTime) : undefined;

        return <div key={event.id} className="flex flex-row items-stretch gap-5 pr-2 text-left bg-slate-100/25">
          <div className="border-r-2" style={{ borderColor: app.selectedCalendar?.colour }}></div>
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
        <div className="flex flex-row items-stretch gap-5 pr-2 text-left">
          <div className="pl-5 text-center grow text-black/50">
            No Events
          </div>
        </div>
      }
    </div>
  </div>);
}

export { Agenda };