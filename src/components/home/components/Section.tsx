import { ReactNode } from "react";

function Section({
  name,
  className,
  children
}: {
  name?: string;
  className?: string;
  children?: ReactNode | undefined;
}) {
  return (
    <div className={className}>
      {name &&
        <h3 className="text-2xl mb-5">{name}</h3>
      }
      {children}
    </div>
  );
}

export { Section };