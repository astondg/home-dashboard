function Agenda() {
  return (
    <div className="flex flex-row">
      <div className="grow flex flex-col gap-5">
        <div className="flex flex-row items-center gap-5 italic text-left pr-2 opacity-50">
          <div className="grow">
            <div>commute to office</div>
            <div className="text-xs">08:00 light traffic</div>
          </div>
          <div>
            <div>32min</div>
          </div>
        </div>

        <div className="flex flex-row items-stretch gap-5 text-left pr-2 bg-slate-100/25 opacity-50">
          <div className="border-blue-500 border-r-2"></div>
          <div className="grow">
            <h3 className="font-semibold">Return locker key</h3>
            <span>arriving at work</span>
          </div>
        </div>

        <div className="flex flex-row items-stretch gap-5 text-left pr-2 bg-slate-100/25 opacity-50">
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

        <hr className="border-t-yellow-300 border-t-4 shadow-[0_-2px_2px_0_rgb(253,224,71,0.5)]" />

        <div className="flex flex-row items-stretch gap-5 italic text-left pr-2 opacity-75">
          <div className="grow">
            <span>light rain 16℃</span>
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

        <div className="flex flex-row items-stretch gap-5 italic text-left pr-2 opacity-75">
          <div className="grow">
            <span>sunset</span>
          </div>
          <div>
            <div>19:00</div>
          </div>
        </div>

        <div className="flex flex-row items-center gap-5 italic text-left pr-2 opacity-75">
          <div className="grow">
            <div>commute to home</div>
            <div className="text-xs">19:00 usual traffic</div>
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
  );
}

export { Agenda };