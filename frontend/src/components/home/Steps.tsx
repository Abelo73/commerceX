const STEPS = [
  { title: "Pick your pair", body: "Choose a colour and size. Every size shows whether it’s in stock right now." },
  { title: "Check out on one page", body: "Address, delivery and payment in one place. The total never changes behind your back." },
  { title: "Track it to your door", body: "Follow your order from paid to delivered, then tell other runners how it went." },
];

/** A real sequence, so it is numbered. */
export function Steps() {
  return (
    <section id="how-it-works" aria-labelledby="steps-title" className="mx-auto max-w-5xl scroll-mt-24 px-4 py-24 sm:px-6">
      <h2 id="steps-title" className="display text-center text-4xl sm:text-5xl">
        Simple from click to door
      </h2>
      <ol className="relative mt-14 grid gap-10 md:grid-cols-3 md:gap-6">
        <span aria-hidden className="border-line absolute top-6 right-[16%] left-[16%] hidden border-t-2 border-dashed md:block" />
        {STEPS.map((step, i) => (
          <li key={step.title} className="relative flex gap-4 md:flex-col md:items-center md:text-center">
            <span className="bg-navy figures relative grid h-12 w-12 shrink-0 place-items-center rounded-full font-bold text-white ring-8 ring-white">
              {i + 1}
            </span>
            <div>
              <h3 className="condensed text-lg font-bold md:mt-3">{step.title}</h3>
              <p className="text-mute mt-1 max-w-xs text-sm">{step.body}</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
