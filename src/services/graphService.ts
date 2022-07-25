// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

// <GetUserSnippet>
import { Client, GraphRequestOptions, PageCollection, PageIterator } from '@microsoft/microsoft-graph-client';
import { AuthCodeMSALBrowserAuthenticationProvider } from '@microsoft/microsoft-graph-client/authProviders/authCodeMsalBrowser';
import { endOfDay, endOfMonth, endOfWeek, startOfDay, startOfMonth, startOfWeek } from 'date-fns';
import { zonedTimeToUtc } from 'date-fns-tz';
import { Calendar, User, Event } from 'microsoft-graph';

let graphClient: Client | undefined = undefined;

function ensureClient(authProvider: AuthCodeMSALBrowserAuthenticationProvider) {
  if (!graphClient) {
    graphClient = Client.initWithMiddleware({
      authProvider: authProvider
    });
  }

  return graphClient;
}

export async function getUser(authProvider: AuthCodeMSALBrowserAuthenticationProvider): Promise<User> {
  ensureClient(authProvider);

  // Return the /me API endpoint result as a User object
  const user: User = await graphClient!.api('/me')
    // Only retrieve the specific fields needed
    .select('displayName,mail,mailboxSettings,userPrincipalName')
    .get();

  return user;
}
// </GetUserSnippet>

// <GetUserMonthCalendarSnippet>
export async function getUserMonthCalendar(authProvider: AuthCodeMSALBrowserAuthenticationProvider,
                                          calendarId: string,
                                          currentDay: Date,
                                          timeZone: string): Promise<Event[]> {
  ensureClient(authProvider);

  // Generate startDateTime and endDateTime query params
  // to display a 7-day window
  const startDateTime = zonedTimeToUtc(startOfMonth(currentDay), timeZone).toISOString();
  const endDateTime = zonedTimeToUtc(endOfMonth(currentDay), timeZone).toISOString();

  // GET /me/calendarview?startDateTime=''&endDateTime=''
  // &$select=subject,organizer,start,end
  // &$orderby=start/dateTime
  // &$top=50
  var response: PageCollection = await graphClient!
    .api(`/me/calendars/${calendarId}/events`)
    .header('Prefer', `outlook.timezone="${timeZone}"`)
    .query({ startDateTime: startDateTime, endDateTime: endDateTime })
    .select('subject,organizer,start,end')
    .orderby('start/dateTime')
    .top(25)
    .get();

  if (response["@odata.nextLink"]) {
    // Presence of the nextLink property indicates more results are available
    // Use a page iterator to get all results
    var events: Event[] = [];

    // Must include the time zone header in page
    // requests too
    var options: GraphRequestOptions = {
      headers: { 'Prefer': `outlook.timezone="${timeZone}"` }
    };

    var pageIterator = new PageIterator(graphClient!, response, (event) => {
      events.push(event);
      return true;
    }, options);

    await pageIterator.iterate();

    return events;
  } else {

    return response.value;
  }
}
// </GetUserMonthCalendarSnippet>

// <GetUserWeekCalendarSnippet>
export async function getUserWeekCalendar(
  authProvider: AuthCodeMSALBrowserAuthenticationProvider,
  calendarId: string,
  currentDay: Date,
  timeZone: string
): Promise<Event[]> {
  ensureClient(authProvider);

  // Generate startDateTime and endDateTime query params
  // to display a 7-day window
  const startDateTime = zonedTimeToUtc(startOfWeek(currentDay), timeZone).toISOString();
  const endDateTime = zonedTimeToUtc(endOfWeek(currentDay), timeZone).toISOString();

  // GET /me/calendarview?startDateTime=''&endDateTime=''
  // &$select=subject,organizer,start,end
  // &$orderby=start/dateTime
  // &$top=50
  var response: PageCollection = await graphClient!
  .api(`/me/calendars/${calendarId}/events`)
  .header('Prefer', `outlook.timezone="${timeZone}"`)
  .query({ startDateTime: startDateTime, endDateTime: endDateTime })
  .select('subject,organizer,start,end,isAllDay')
  .orderby('start/dateTime')
  .top(25)
  .get();

  if (response["@odata.nextLink"]) {
    // Presence of the nextLink property indicates more results are available
    // Use a page iterator to get all results
    var events: Event[] = [];

    // Must include the time zone header in page
    // requests too
    var options: GraphRequestOptions = {
    headers: { 'Prefer': `outlook.timezone="${timeZone}"` }
    };

    var pageIterator = new PageIterator(graphClient!, response, (event) => {
      events.push(event);
      return true;
    }, options);
    await pageIterator.iterate();
    return events;
  } else {
    return response.value;
  }
}
// </GetUserWeekCalendarSnippet>

// <GetUserDayCalendarSnippet>
export async function getUserDayCalendar(
  authProvider: AuthCodeMSALBrowserAuthenticationProvider,
  calendarId: string,
  currentDay: Date,
  timeZone: string
): Promise<Event[]> {
  ensureClient(authProvider);

  // Generate startDateTime and endDateTime query params
  // to display a 7-day window
  const startDateTime = zonedTimeToUtc(startOfDay(currentDay), timeZone).toISOString();
  const endDateTime = zonedTimeToUtc(endOfDay(currentDay), timeZone).toISOString();

  // GET /me/calendarview?startDateTime=''&endDateTime=''
  // &$select=subject,organizer,start,end
  // &$orderby=start/dateTime
  // &$top=50
  var response: PageCollection = await graphClient!
  .api(`/me/calendars/${calendarId}/events`)
  .header('Prefer', `outlook.timezone="${timeZone}"`)
  .query({ startDateTime: startDateTime, endDateTime: endDateTime })
  .select('subject,location,start,end,isAllDay')
  .orderby('start/dateTime')
  .top(25)
  .get();

  if (response["@odata.nextLink"]) {
    // Presence of the nextLink property indicates more results are available
    // Use a page iterator to get all results
    var events: Event[] = [];

    // Must include the time zone header in page
    // requests too
    var options: GraphRequestOptions = {
    headers: { 'Prefer': `outlook.timezone="${timeZone}"` }
    };

    var pageIterator = new PageIterator(graphClient!, response, (event) => {
      events.push(event);
      return true;
    }, options);
    await pageIterator.iterate();
    return events;
  } else {
    return response.value;
  }
}
// </GetUserDayCalendarSnippet>

export async function getUserCalendars(
  authProvider: AuthCodeMSALBrowserAuthenticationProvider
): Promise<Calendar[]> {
  ensureClient(authProvider);

  // GET /me/calendarview?startDateTime=''&endDateTime=''
  // &$select=subject,organizer,start,end
  // &$orderby=start/dateTime
  // &$top=50
  var response: PageCollection = await graphClient!
  .api(`/me/calendars`)
  .select('id,name,color,hexColor')
  .top(25)
  .get();

  if (response["@odata.nextLink"]) {
    // Presence of the nextLink property indicates more results are available
    // Use a page iterator to get all results
    var calendars: Calendar[] = [];

    // Must include the time zone header in page
    // requests too
    var options: GraphRequestOptions = {};

    var pageIterator = new PageIterator(graphClient!, response, (calendar: Calendar) => {
      calendars.push(calendar);
      return true;
    }, options);
    await pageIterator.iterate();
    return calendars;
  } else {
    return response.value;
  }
}

// <CreateEventSnippet>
export async function createEvent(authProvider: AuthCodeMSALBrowserAuthenticationProvider,
                                  calendarId: string,
                                  newEvent: Event): Promise<Event> {
  ensureClient(authProvider);

  // POST /me/events
  // JSON representation of the new event is sent in the
  // request body
  return await graphClient!
    .api(`/me/calendars/${calendarId}/events`)
    .post(newEvent);
}
// </CreateEventSnippet>