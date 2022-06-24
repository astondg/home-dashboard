function Budget() {
  return (
    <div className="flex flex-col gap-5 text-left">
      <div className="flex flex-col gap-2">
        <div className="flex flex-row justify-center gap-2 px-5 text-2xl">
          <span>$290</span>
          <span className="opacity-75">/ $400</span>
        </div>
        <div className="progress-container">
          <progress max="400" value="290"></progress>
        </div>
      </div>
      
      <div className="flex flex-col gap-2">
        <div>
          <div className="flex flex-row gap-2 text-red-600">
            <span className="grow mr-10">entertainment</span>
            <span>$92.30</span>
            <span className="opacity-75">/ $90.00</span>
          </div>
          <span className="font-semibold text-sm opacity-50">quarterly</span>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <div>
          <div className="flex flex-row gap-2">
            <span className="grow mr-10">transport</span>
            <span>$32.50</span>
            <span className="opacity-75">/ $85.00</span>
          </div>
          <span className="font-semibold text-sm opacity-50">weekly</span>
        </div>
        <div>
          <div className="flex flex-row gap-2">
            <span className="grow mr-10">groceries</span>
            <span>$176.00</span>
            <span className="opacity-75">/ $200.00</span>
          </div>
          <span className="font-semibold text-sm opacity-50">weekly</span>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <div>
          <div className="flex flex-row gap-2">
            <span className="grow mr-10">holidays</span>
            <span>$1800.00</span>
            <span className="opacity-75">/ $3800.00</span>
          </div>
          <span className="font-semibold text-sm opacity-50">yearly</span>
        </div>
      </div>
    </div>
  );
}

export { Budget };