# Product Requirements Document (PRD)

## Project

## World Cup 2026 Prediction Game

Internal company competition where participants predict World Cup match outcomes and compete on a leaderboard throughout the tournament.

Target audience: ~100-200 internal participants.

⸻

Vision

Create a fun, engaging prediction platform that keeps employees involved throughout the entire World Cup rather than allowing them to submit all predictions upfront and forget about it.

The application should encourage daily engagement, friendly competition, and transparency while preventing participants from copying each other’s predictions before matches begin.

⸻

User Roles

Guest

Unauthenticated visitor.

Permissions

Can:

* View leaderboard
* View tournament groups
* View fixtures
* View completed match results
* View knockout bracket
* View team squads
* View match lineups (when available)
* View completed predictions after matches are finished

Cannot:

* Submit predictions
* See future predictions
* See participant profile details beyond public information

⸻

Participant

Authenticated user approved by administrator.

Permissions

Can:

* Submit tournament winner prediction
* Submit match predictions during open prediction windows
* View own predictions
* View leaderboard
* View public tournament data
* View completed predictions of other participants after match completion
* View tournament winner predictions of all participants at any time

Cannot:

* Modify predictions after lock time
* View future predictions of other participants
* Predict matches outside allowed prediction windows

⸻

Administrator

Full access.

User Management

* Review registration requests
* Approve participants
* Generate temporary password
* Send activation email
* Resend activation email
* Enable participant
* Disable participant

Prediction Management

* Open prediction window manually
* Close prediction window manually
* Send prediction reminders manually
* View all predictions
* Edit predictions (optional emergency feature)

Tournament Management

* Manage groups
* Manage fixtures
* Manage squads
* Manage lineups
* Enter match results manually
* Update knockout progression manually
* Override points if required

⸻

Registration Flow

Step 1

User submits:

* First Name
* Last Name
* Email

Status:

Pending Approval

⸻

Step 2

Administrator reviews request.

Options:

* Approve
* Reject

⸻

Step 3

System:

* Generates temporary password
* Sends activation email

Status:

Active

⸻

Step 4

User logs in.

Must:

* Change password

⸻

Tournament Winner Prediction

Before tournament kickoff each participant must predict:

* World Cup Winner

Example:

Brazil

Scoring

Correct prediction:

+10 points

Incorrect prediction:

0 points

Visibility

Visible to everyone immediately after submission.

This creates discussion and fun before the tournament starts.

⸻

Match Prediction Rules

Prediction Window

Predictions are opened per matchday.

Rule

Participants cannot predict the entire tournament upfront.

Prediction becomes available:

Maximum 24 hours before kickoff

Example:

Match starts:

June 15 18:00

Prediction opens:

June 14 18:00

⸻

Lock Rule

Predictions become read-only:

1 hour before kickoff

Example:

Match starts:

18:00

Prediction locked:

17:00

⸻

Prediction Type

Participant predicts:

Home Team Score

Away Team Score

Example:

Spain 2 - 1 Germany

⸻

Scoring System

Exact Score

Prediction:

Spain 2-1 Germany

Actual:

Spain 2-1 Germany

Score:

+3 points

⸻

Correct Draw

Prediction:

1-1

Actual:

0-0

Score:

+2 points

Reason:

Correctly predicted draw outcome.

⸻

Correct Winner

Prediction:

Spain 2-1 Germany

Actual:

Spain 1-0 Germany

Score:

+1 point

Reason:

Correct winner predicted.

⸻

Wrong Prediction

Prediction:

Spain win

Actual:

Germany win

Score:

0 points

⸻

Prediction Visibility Rules

These are critical.

Before Match Completion

Participants cannot see:

* Other users’ predictions

Guests cannot see:

* Any future predictions

Administrators can see:

* All predictions

⸻

After Match Completion

Everyone can see:

* All predictions
* Points earned

This creates transparency while preventing copying.

⸻

Leaderboard

Ranking Criteria

1. Total Points
2. Exact Score Count
3. Correct Winner Count
4. Tournament Winner Prediction
5. Earliest Registration (optional)

⸻

Leaderboard Design

Each row contains:

* Participant Avatar
* Country Flag (favorite team / winner prediction)
* Name
* Current Rank
* Points
* Exact Predictions Count

Animation Ideas

* Podium animation for Top 3
* Rising/falling rank arrows
* Flag waving effect
* Confetti for weekly leader
* Smooth leaderboard transitions

⸻

Tournament Pages

Groups

Display:

* Teams
* Played
* Wins
* Draws
* Losses
* Goals
* Points

⸻

Fixtures

Display:

* Upcoming matches
* Live matches (future enhancement)
* Completed matches

⸻

Match Details

Display:

* Result
* Venue
* Lineups (if available)
* Match events (future enhancement)

⸻

Team Page

Display:

* Squad
* Team information
* Flag
* Group

⸻

Knockout Stage

Visual bracket.

Rounds:

* Round of 32
* Round of 16
* Quarter Finals
* Semi Finals
* Third Place Match
* Final

Animated progression preferred.

⸻

Notifications

Administrator can manually trigger:

Prediction Reminder

Example:

“Predictions for today’s matches close in 2 hours.”

Match Result Published

Example:

“Results published. Leaderboard updated.”

Tournament Winner Scored

Example:

“World Cup winner points awarded.”

⸻

Non-Functional Requirements

Performance

* 100-200 users
* Low traffic
* Mobile friendly
* Responsive UI

⸻

Security

* Password reset
* Session authentication
* Role-based authorization
* Audit log for admin actions

⸻

Recommended Technology Stack

Since you’re a Java developer:

Backend

* Java 21
* Spring Boot 3.x
* Spring MVC
* Spring Security
* Spring Data JPA
* PostgreSQL

Frontend

* Thymeleaf + HTMX (simple)
    OR
* React + Vite (more modern)

For this project I would actually recommend:

Spring Boot + Thymeleaf + HTMX

because:

* single deployable application
* minimal complexity
* fast development
* perfect for 100 users
* easier maintenance after the tournament

UI

* Tailwind CSS
* Alpine.js
* Framer-style CSS animations
* SVG animated bracket

⸻

Future Enhancements

Phase 2:

* Automatic FIFA data integration
* Automatic result synchronization
* Automatic lineups
* Email reminders
* Live scoring
* Team statistics
* Historical leaderboards
* Multiple tournaments support (Euro, Copa America, World Cup)