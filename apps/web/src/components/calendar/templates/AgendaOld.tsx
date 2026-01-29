function AgendaOld() {
  return (<>
    <h3 className="text-2xl mb-5 flex flex-row justify-between items-end pl-5 pr-2">
      <div>Tuesday</div>
      <div>12<span className="text-lg align-super">th</span> of July</div>
    </h3>
    <div className="flex flex-row">
      <div className="grow flex flex-col gap-5">

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

export { AgendaOld };