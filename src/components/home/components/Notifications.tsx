function Notifications() {
  return (
    <div className="flex flex-row justify-start gap-20 px-10 py-5 bg-gray-500/10">
      <div className="text-2xl px-5 py-2 bg-red-500/75 text-white">storm in 30min</div>
      <div className="text-2xl px-5 py-2 bg-blue-500/75 text-white">leave for Lunch with Liz</div>
      <div className="text-2xl px-5 py-2 bg-green-500/75 text-white">bin day</div>
    </div>
  )
}

export { Notifications };