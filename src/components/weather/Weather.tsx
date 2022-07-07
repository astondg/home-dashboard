import '../../weather-icons.min.css';

function Weather() {
  return (<>
    <h3 className="mb-5 flex flex-col justify-center pl-5 pr-2">
        <i className="wi wi-showers text-6xl mr-2 mb-6"></i>        
        <div className="text-xl">8&deg;C - 18&deg;C</div>
    </h3>
    <div className="flex flex-row gap-5 justify-center">
        <div className="flex flex-col items-stretch gap-2 text-left pr-2">
          <div className="text-sm text-center">
            07
          </div>
          <div className="grow text-3xl text-center">
            <i className="wi wi-sunrise"></i>
          </div>
          <div className="font-semibold text-center">
            10&deg;C
          </div>
        </div>

        <div className="flex flex-col items-stretch gap-2 text-left pr-2">
          <div className="text-sm text-center">
            09
          </div>
          <div className="grow text-3xl text-center">
            <i className="wi wi-cloudy"></i>
          </div>
          <div className="font-semibold text-center">
            12&deg;C
          </div>
        </div>

        <div className="flex flex-col items-stretch text-left pr-2">
          <div className="text-sm text-center">
            11
          </div>
          <div className="grow text-3xl text-center">
            <i className="wi wi-showers"></i>
          </div>
          <div className="font-semibold text-center mt-3">
            16&deg;C
          </div>
        </div>

        <div className="flex flex-col items-stretch gap-2 text-left pr-2">
          <div className="text-sm text-center">
            12
          </div>
          <div className="grow text-3xl text-center">
            <i className="wi wi-day-sunny"></i>
          </div>
          <div className="font-semibold text-center">
            18&deg;C
          </div>
        </div>

        <div className="flex flex-col items-stretch gap-2 text-left pr-2">
          <div className="text-sm text-center">
            17
          </div>
          <div className="grow text-3xl text-center">
            <i className="wi wi-sunset"></i>
          </div>
          <div className="font-semibold text-center">
            16&deg;C
          </div>
        </div>

    </div>
  </>);
}

export { Weather };