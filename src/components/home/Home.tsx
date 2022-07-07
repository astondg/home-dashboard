import React, { useCallback, useEffect, useState } from "react";
import { useAppContext } from "../../hooks";
import { Budget } from "../budget/Budget";
import { Agenda } from "../calendar/Agenda";
import { AgendaOld } from "../calendar/templates/AgendaOld";
import { AgendaWeather } from "../calendar/templates/AgendaWeather";
import { Calendar } from "../calendar/Calendar";
import { WeekPlanner } from "../calendar/WeekPlanner";
import { Notes } from "../notes/Notes";
import { Weather } from "../weather/Weather";
import { Clock } from "./components/Clock";
import { Notifications } from "./components/Notifications";
import { Section } from "./components/Section";
import { getUserCalendars } from "../../services";

function Home() {
  const app = useAppContext();
  const [allCalendars, setAllCalendars] = useState<{ name: string; id: string; }[]>();
  const [showMonthCalendar, setShowMonthCalendar] = useState<boolean>(false);

  const handleSignInClick = useCallback((event: React.MouseEvent<HTMLButtonElement>) => {
    if (app.user || !app.signIn) return;
    app.signIn(event);
  }, [app.user, app.signIn]);

  const handleCalendarClick = useCallback((calendarId: string) => () => {
    if (!app.selectCalendar) return;
    app.selectCalendar(calendarId);
  }, [app.selectCalendar]);

  const handleMonthToggleClick = useCallback(() => {
    setShowMonthCalendar(current => !current);
  }, []);

  useEffect(() => {
    let isMounted = true;
    const loadCalendars = async () => {
      if (!app.authProvider) return;
      const calendars = await getUserCalendars(app.authProvider);
      setAllCalendars(calendars.map(calendar => ({ id: calendar.id || "", name: calendar.name || "" })))
    };

    if (!app.user || app.selectedCalendarId || !app.selectCalendar) {
      setAllCalendars(undefined);
    } else {
      loadCalendars();
    }

    return () => { isMounted = false };
  }, [app.authProvider, app.selectedCalendarId]);

  return (<>
    <div className="flex flex-col h-full overflow-hidden">
      {!app.user &&
        <div className="flex flex-row justify-end px-4 py-2">
          <button className="text-sm" type="button" onClick={handleSignInClick}>sign in</button>
        </div>
      }
      {/* <div>
        <Clock />
      </div> */}
      {/* <Notifications /> */}
      <div className="grow flex flex-row justify-around gap-20 px-10 py-5">
        {/* <Section className="w-1/5">
          <AgendaOld />
        </Section>
        <Section className="w-1/5">
          <Weather />
        </Section>
        <Section className="w-1/5">
          <AgendaWeather />
        </Section> */}
        {showMonthCalendar &&
          <Section className="grow">
            <Calendar />
          </Section>
        }
        {!showMonthCalendar && <>
          <Section className="w-1/5 flex flex-col justify-between">
            <Agenda />
            <button className="bg-gray-400 text-white py-2" type="button" onClick={handleMonthToggleClick}>Month View</button>
          </Section>
          <Section className="grow">
            <WeekPlanner />
          </Section>
        </>}
        {/* <div className="w-1/5 flex flex-col justify-start">
          <Section className="grow" name="notes">
            <Notes />
          </Section>
          <Section name="budget">
            <Budget />
          </Section>
        </div> */}
      </div>
    </div>
    {allCalendars &&
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full">
        <div className="relative top-20 mx-auto p-5 border w-96 shadow-lg rounded-md bg-white">
          {allCalendars.map(calendar => {
            return <div>
              <button type="button" onClick={handleCalendarClick(calendar.id)}>{calendar.name}</button>
            </div>;
          })}
        </div>
      </div>
    }
  </>);
}

export { Home };