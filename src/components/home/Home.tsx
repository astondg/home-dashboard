import { Budget } from "../budget/Budget";
import { Agenda } from "../calendar/Agenda";
import { Calendar } from "../calendar/Calendar";
import { Notes } from "../notes/Notes";
import { Clock } from "./components/Clock";
import { Notifications } from "./components/Notifications";
import { Section } from "./components/Section";

function Home() {
  return (
    <div className="flex flex-col h-full">
      <div>
        <Clock />
      </div>
      <Notifications />
      <div className="grow flex flex-row justify-around gap-20 px-10 py-5">
        <Section className="w-1/5" name="my day">
          <Agenda />
        </Section>
        <Section className="grow">
          <Calendar />
        </Section>
        <div className="w-1/5 flex flex-col justify-start">
          <Section className="grow" name="notes">
            <Notes />
          </Section>
          <Section name="budget">
            <Budget />
          </Section>
        </div>
      </div>
    </div>
  );
}

export { Home };