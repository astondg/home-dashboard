import { useEffect, useState } from "react";

function Clock() {
  const [value, setValue] = useState(new Date());
  const days = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"];
  const months = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];

  useEffect(() => {
    const interval = setInterval(() => setValue(new Date()), 1000);

    return () => {
      clearInterval(interval);
    };
  }, []);

  return (
    <div className="flex flex-row justify-center items-center gap-x-8 text-4xl px-10 py-5">
      <div className="flex flex-col items-center">
        <h2 className="font-bold">{days[value.getDay()]}</h2>
        <div>
          <span className="mr-2">{value.getDate()}</span>
          <span className="mr-2">{months[value.getMonth()]}</span>
          <span>{value.getFullYear()}</span>
        </div>
      </div>
      <h1 className="text-8xl grow">{`${value.getHours()}:${value.getMinutes()}:${value.getSeconds()}`}</h1>
      <h2 className="text-6xl">18&deg;C</h2>
    </div>
  );
}

export { Clock };