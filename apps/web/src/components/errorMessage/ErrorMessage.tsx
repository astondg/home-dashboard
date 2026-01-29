// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

// <ErrorMessageSnippet>
import { useAppContext } from '../../hooks/useAppContext';

export default function ErrorMessage() {
  const app = useAppContext();

  if (app.error) {
    return (
      <div>
        <p className="mb-3">{app.error.message}</p>
        { app.error.debug ?
          <pre className="alert-pre border bg-light p-2"><code>{app.error.debug}</code></pre>
          : null
        }
      </div>
    );
  }

  return null;
}
// </ErrorMessageSnippet>