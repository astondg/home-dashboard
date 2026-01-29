import '../../weather-icons.min.css';

function Agenda() {
  return (<>
    <h3 className="text-2xl mb-5 flex flex-row justify-between items-end pl-5 pr-2">
      <div className="text-left">
        <div>Tuesday</div>
        <div className="text-xl">12<span className="text-base align-super">th</span> of July</div>
      </div>
      <div>
        <i className="wi wi-showers mr-2 pb-1"></i>        
        <div className="text-xl">8&deg;C - 18&deg;C</div>
      </div>
    </h3>
    <div className="flex flex-row">
      <div className="grow flex flex-col gap-5">
        <div className="flex flex-row items-stretch gap-5 italic text-left pr-2 opacity-25">
          <div className="grow pl-5">
            <i className="wi wi-sunrise mr-2"></i>
            <span className="mr-4">10&deg;C</span>
            <span>sunrise</span>
          </div>
          <div>
            <div>07:00</div>
          </div>
        </div>

        <div className="flex flex-row items-center gap-5 italic text-left pr-2 opacity-25">
          <div className="grow pl-5">
            <div>commute to office</div>
            <div className="text-xs">08:00 light traffic</div>
          </div>
          <div>
            <div>32min</div>
          </div>
        </div>

        <div className="flex flex-row items-stretch gap-5 text-left pr-2 bg-slate-100/25 opacity-25">
          <div className="border-blue-500 border-r-2"></div>
          <div className="grow">
            <h3 className="font-semibold">Return locker key</h3>
            <span>arriving at work</span>
          </div>
        </div>

        <div className="flex flex-row items-stretch gap-5 text-left pr-2 bg-slate-100/25 opacity-25">
          <div className="border-orange-500 border-r-2"></div>
          <div className="grow">
            <h3 className="font-semibold">Standup</h3>
            <span>The office</span>
          </div>
          <div className="font-semibold">
            <div>09:30</div>
            <div>10:45</div>
          </div>
        </div>

        <hr className="border-t-yellow-300 border-t-4 shadow-[0_-2px_2px_0_rgb(253,224,71,0.5)] rounded" />

        <div className="flex flex-row items-stretch gap-5 italic text-left pr-2 opacity-60">
          <div className="grow pl-5">
            <i className="wi wi-showers mr-2"></i>
            <span className="mr-4">16&deg;C</span>
            <span>light rain</span>
          </div>
          <div>
            <div>11:00</div>
          </div>
        </div>

        <div className="flex flex-row items-stretch gap-5 text-left pr-2 bg-slate-100/25">
          <div className="border-orange-500 border-r-2"></div>
          <div className="grow">
            <h3 className="font-semibold">Lunch with Liz</h3>
            <span>Grill'd Wintergarden</span>
          </div>
          <div className="font-semibold">
            <div>11:30</div>
            <div>12:15</div>
          </div>
        </div>

        <div className="flex flex-row items-stretch gap-5 italic text-left pr-2 opacity-60">
          <div className="grow pl-5">
            <i className="wi wi-sunset mr-2"></i>
            <span className="mr-4">16&deg;C</span>
            <span>sunset</span>
          </div>
          <div>
            <div>17:00</div>
          </div>
        </div>

        <div className="flex flex-row items-center gap-5 italic text-left pr-2 opacity-60">
          <div className="grow pl-5">
            <div>commute to home</div>
            <div className="text-xs">17:00 usual traffic</div>
          </div>
          <div>
            <div>35min</div>
          </div>
        </div>

        <div className="flex flex-row items-stretch gap-5 text-left pr-2 bg-slate-100/25">
          <div className="border-orange-500 border-r-2"></div>
          <div className="grow">
            <h3 className="font-semibold">Tennis with Steve</h3>
            <span>Total Tennis Aspley</span>
          </div>
          <div className="font-semibold">
            <div>18:00</div>
            <div>18:30</div>
          </div>
        </div>
      </div>
    </div>
  </>);
}

export { Agenda };