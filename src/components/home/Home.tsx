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
import { Calendar as MicrosoftCalendar } from "microsoft-graph";

function Home() {
  const app = useAppContext();
  const [allCalendars, setAllCalendars] = useState<MicrosoftCalendar[]>();
  const [showMonthCalendar, setShowMonthCalendar] = useState<boolean>(false);

  const handleSignInClick = useCallback((event: React.MouseEvent<HTMLButtonElement>) => {
    if (app.user || !app.signIn) return;
    app.signIn(event);
  }, [app.user, app.signIn]);

  const handleCalendarClick = useCallback((calendar: MicrosoftCalendar) => () => {
    if (!app.selectCalendar) return;
    app.selectCalendar(calendar);
  }, [app.selectCalendar]);

  const handleMonthToggleClick = useCallback(() => {
    setShowMonthCalendar(current => !current);
  }, []);

  useEffect(() => {
    let isMounted = true;
    const loadCalendars = async () => {
      if (!app.authProvider) return;
      const calendars = await getUserCalendars(app.authProvider);
      if (!isMounted) return;
      setAllCalendars(calendars);
    };

    if (!app.user || app.selectedCalendar?.id || !app.selectCalendar) {
      setAllCalendars(undefined);
    } else {
      loadCalendars();
    }

    return () => { isMounted = false };
  }, [app.authProvider, app.selectedCalendar]);

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
      <div className="flex flex-row justify-around h-full gap-20 px-10 py-5 grow">
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
          <Section className="flex flex-col justify-between w-1/5">
            <Agenda />
            <button className="py-2 text-lg border-2" type="button" onClick={handleMonthToggleClick}>Month View</button>
          </Section>
          <Section className="grow">
            <WeekPlanner />
          </Section>
        </>}
        {/* <div className="flex flex-col justify-start w-1/5">
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
      <div className="fixed inset-0 w-full h-full overflow-y-auto bg-gray-600 bg-opacity-50">
        <div className="relative p-5 mx-auto bg-white border rounded-md shadow-lg top-20 w-96">
          {allCalendars.map(calendar => {
            return <div key={calendar.name}>
              <button type="button" onClick={handleCalendarClick(calendar)}>{calendar.name}</button>
            </div>;
          })}
        </div>
      </div>
    }
  </>);
}

export { Home };