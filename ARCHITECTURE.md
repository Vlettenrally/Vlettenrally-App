# Vlettenrally App - Architecture Document

## Executive Summary

This document outlines the architecture for the Vlettenrally App, a comprehensive mobile application for managing and participating in Dutch scouting boat rally events. The app serves two primary user groups: participants (teams on boats) and administrators (rally organizers).

## Requirements Analysis

Based on GitHub issues #1-11, the app requires:

### Participant Features
1. **Check-in System** (Issue #1)
   - Game selection
   - Boat number registration
   - Emergency contact registration
   - Participant name registration
   - Rival team selection
   - Game instructions display

2. **Messaging System** (Issues #2-5)
   - Countdown notifications (30s to 60min intervals)
   - Achievement notifications (points, checkpoints)
   - Rival team updates
   - Statistics notifications (speed records, rankings)
   - General messages from rally desk
   - Weather information

### Administrative Features
3. **Game Configuration** (Issue #6)
   - Create/import rally games
   - Define game start/end times
   - Configure starting area times
   - Configure checkpoint opening times
   - Manage game settings

4. **Team Management** (Issue #7)
   - Import/create teams
   - Assign boat numbers
   - Manage team details
   - Configure rivals
   - Track device check-ins

5. **Message Management** (Issue #9)
   - Create message templates
   - Manage message variants
   - Send manual messages
   - Target specific teams or groups

6. **Automation System** (Issue #10)
   - Rule-based automated notifications
   - Trigger conditions (points, checkpoints, speed, rankings)
   - Time-based triggers
   - Countdown automation
   - Rate limiting

7. **System Configuration** (Issue #8)
   - API settings
   - System parameters

8. **Admin Notifications** (Issue #11)
   - Speed limit alerts
   - Configurable thresholds

## Architecture Options

### Option 1: Monolithic Mobile-First Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Mobile App Layer                         │
│  ┌──────────────────────┐          ┌──────────────────────────┐ │
│  │  Participant UI      │          │  Admin UI (Web)          │ │
│  │  - React Native      │          │  - React Web             │ │
│  │  - Check-in          │          │  - Admin Dashboard       │ │
│  │  - Notifications     │          │  - Configuration         │ │
│  └──────────────────────┘          └──────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API Gateway (REST)                          │
│                      - Express.js / NestJS                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Business Logic Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐│
│  │ Game Service │  │ Team Service │  │ Notification Service   ││
│  │              │  │              │  │ - Rules Engine         ││
│  │              │  │              │  │ - Scheduler            ││
│  └──────────────┘  └──────────────┘  └────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Data Layer                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  PostgreSQL  │  │    Redis     │  │  Firebase/FCM        │ │
│  │  - Games     │  │  - Sessions  │  │  - Push Notifications│ │
│  │  - Teams     │  │  - Cache     │  │                      │ │
│  │  - Messages  │  │  - Pub/Sub   │  │                      │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

**Pros:**
- Simpler to develop and deploy
- Single codebase for business logic
- Easier to maintain initially
- Lower infrastructure costs
- Good for MVP

**Cons:**
- Harder to scale specific components
- Single point of failure
- Deployment affects entire system

### Option 2: Microservices Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend Layer                           │
│  ┌──────────────────────┐          ┌──────────────────────────┐ │
│  │  Mobile App          │          │  Admin Dashboard         │ │
│  │  - React Native      │          │  - React + TypeScript    │ │
│  │  - Expo              │          │  - Material-UI           │ │
│  └──────────────────────┘          └──────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway                                   │
│                    - Kong / AWS API Gateway                      │
│                    - Authentication                              │
│                    - Rate Limiting                               │
└─────────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┼─────────────────┐
            ▼                 ▼                 ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────┐
│  Game Service    │ │  Team Service    │ │ Notification Service │
│  - REST API      │ │  - REST API      │ │  - WebSocket         │
│  - PostgreSQL    │ │  - PostgreSQL    │ │  - Redis Pub/Sub     │
│  - Node.js       │ │  - Node.js       │ │  - Rules Engine      │
└──────────────────┘ └──────────────────┘ └──────────────────────┘
                                                     │
            ┌────────────────────────────────────────┤
            ▼                 ▼                      ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────┐
│ Message Service  │ │ Automation       │ │  Analytics Service   │
│ - Templates      │ │ Service          │ │  - Statistics        │
│ - PostgreSQL     │ │  - Scheduler     │ │  - Rankings          │
│ - Node.js        │ │  - Event-driven  │ │  - Speed tracking    │
└──────────────────┘ └──────────────────┘ └──────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Shared Infrastructure                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  PostgreSQL  │  │    Redis     │  │  Firebase Cloud      │ │
│  │  - Primary   │  │  - Cache     │  │  Messaging (FCM)     │ │
│  │    Data      │  │  - Queue     │  │  - Push Delivery     │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │   RabbitMQ   │  │    Logging   │  │    Monitoring        │ │
│  │  - Events    │  │  - ELK Stack │  │  - Prometheus        │ │
│  │  - Messages  │  │              │  │  - Grafana           │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

**Pros:**
- Each service can scale independently
- Technology flexibility per service
- Fault isolation
- Team autonomy
- Better for long-term growth

**Cons:**
- More complex to develop
- Higher operational overhead
- Distributed system challenges
- Higher infrastructure costs
- Overkill for initial MVP

### Option 3: Serverless Architecture (Recommended)

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend Layer                           │
│  ┌──────────────────────┐          ┌──────────────────────────┐ │
│  │  Mobile App          │          │  Admin Web App           │ │
│  │  - React Native      │          │  - React + TypeScript    │ │
│  │  - Expo              │          │  - Hosted on Vercel/     │ │
│  │  - Offline-first     │          │    Netlify               │ │
│  └──────────────────────┘          └──────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Firebase / Supabase                           │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Authentication - JWT tokens, Role-based access             ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Firestore / PostgreSQL (Supabase)                          ││
│  │  Collections/Tables:                                        ││
│  │  - games, teams, checkpoints, messages, automations         ││
│  │  - Real-time subscriptions                                  ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Cloud Functions / Edge Functions                           ││
│  │  - Check-in logic                                           ││
│  │  - Message processing                                       ││
│  │  - Automation triggers                                      ││
│  │  - Statistics calculation                                   ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Cloud Messaging (FCM)                                      ││
│  │  - Push notifications to devices                            ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Cloud Scheduler                                            ││
│  │  - Countdown timers                                         ││
│  │  - Periodic statistics                                      ││
│  │  - Automation evaluation                                    ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

**Pros:**
- Scales automatically
- Pay-per-use pricing
- Reduced operational burden
- Fast development with BaaS features
- Real-time capabilities built-in
- Good balance for this use case

**Cons:**
- Vendor lock-in (mitigated by using standard APIs)
- Cold start latency
- Limited control over infrastructure

## Recommended Architecture: Serverless with Supabase

### Technology Stack

#### Frontend
- **Mobile App**: React Native + Expo
  - Cross-platform (iOS/Android)
  - Rapid development
  - Good community support
  - Native push notification support
  
- **Admin Dashboard**: React + TypeScript
  - Modern, maintainable
  - Component reuse with mobile app
  - Hosted on Vercel/Netlify

#### Backend
- **Backend-as-a-Service**: Supabase
  - Open-source (less vendor lock-in than Firebase)
  - PostgreSQL database (familiar, powerful)
  - Real-time subscriptions
  - Built-in authentication
  - Edge Functions (Deno)
  - Row Level Security (RLS)

- **Alternative**: Firebase
  - More mature ecosystem
  - Better mobile SDK integration
  - Firestore for real-time data
  - Cloud Functions (Node.js)

#### Infrastructure
- **Push Notifications**: Firebase Cloud Messaging (FCM)
- **File Storage**: Supabase Storage / Firebase Storage
- **Monitoring**: Sentry for error tracking
- **Analytics**: Mixpanel or PostHog

### Database Schema

```sql
-- Games (Rally Events)
CREATE TABLE games (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name VARCHAR(255) NOT NULL,
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP NOT NULL,
  starting_area_active_time TIMESTAMP,
  start_signal_time TIMESTAMP,
  status VARCHAR(50) DEFAULT 'planned', -- planned, active, completed
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Checkpoints
CREATE TABLE checkpoints (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  game_id UUID REFERENCES games(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  opening_time TIMESTAMP NOT NULL,
  closing_time TIMESTAMP,
  points INTEGER DEFAULT 0,
  latitude DECIMAL(10, 8),
  longitude DECIMAL(11, 8),
  created_at TIMESTAMP DEFAULT NOW()
);

-- Teams
CREATE TABLE teams (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  game_id UUID REFERENCES games(id) ON DELETE CASCADE,
  boat_number VARCHAR(50) UNIQUE NOT NULL,
  scouting_group VARCHAR(255) NOT NULL,
  emergency_contact VARCHAR(255),
  participant_names TEXT[],
  special_notes TEXT,
  booking_id VARCHAR(255),
  checked_in BOOLEAN DEFAULT FALSE,
  checked_in_at TIMESTAMP,
  total_points INTEGER DEFAULT 0,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Rivals (Many-to-Many relationship)
CREATE TABLE team_rivals (
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  rival_team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  PRIMARY KEY (team_id, rival_team_id)
);

-- Device Registrations
CREATE TABLE device_registrations (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  device_token VARCHAR(255) UNIQUE NOT NULL,
  platform VARCHAR(50), -- ios, android
  registered_at TIMESTAMP DEFAULT NOW(),
  last_active TIMESTAMP DEFAULT NOW()
);

-- Team Achievements (Points, Checkpoints)
CREATE TABLE team_achievements (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  checkpoint_id UUID REFERENCES checkpoints(id) ON DELETE CASCADE,
  points_earned INTEGER DEFAULT 0,
  achieved_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(team_id, checkpoint_id)
);

-- Speed Records
CREATE TABLE speed_records (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  speed DECIMAL(5, 2) NOT NULL, -- km/h
  latitude DECIMAL(10, 8),
  longitude DECIMAL(11, 8),
  recorded_at TIMESTAMP DEFAULT NOW()
);

-- Message Templates
CREATE TABLE message_templates (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  title VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL, -- general, countdown, checkpoint, rallydesk
  content TEXT NOT NULL,
  icon VARCHAR(255),
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Message Variants (for A/B testing or context)
CREATE TABLE message_variants (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  template_id UUID REFERENCES message_templates(id) ON DELETE CASCADE,
  variant_name VARCHAR(255),
  content TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Automations
CREATE TABLE automations (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name VARCHAR(255) NOT NULL,
  game_id UUID REFERENCES games(id) ON DELETE CASCADE,
  is_active BOOLEAN DEFAULT TRUE,
  
  -- Conditions
  date_time_start TIMESTAMP,
  date_time_end TIMESTAMP,
  active_during_game BOOLEAN DEFAULT TRUE,
  
  -- Triggers
  trigger_type VARCHAR(50), -- points, checkpoint, speed, ranking
  trigger_target VARCHAR(50), -- own_team, rival
  trigger_value JSONB, -- flexible storage for trigger parameters
  
  -- Message
  message_type VARCHAR(50), -- general, countdown, checkpoint
  message_template_id UUID REFERENCES message_templates(id),
  countdown_target VARCHAR(50), -- start_area, start_signal, checkpoint_opening
  countdown_checkpoint_id UUID REFERENCES checkpoints(id),
  countdown_intervals INTEGER[], -- [30, 60, 120, 300, 600, 1200, 1800, 3600] seconds
  
  -- Rate limiting
  max_notifications_per_interval INTEGER,
  interval_minutes INTEGER,
  
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Sent Messages (for tracking and rate limiting)
CREATE TABLE sent_messages (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
  automation_id UUID REFERENCES automations(id) ON DELETE SET NULL,
  message_type VARCHAR(50),
  content TEXT NOT NULL,
  icon VARCHAR(255),
  sent_at TIMESTAMP DEFAULT NOW(),
  delivered BOOLEAN DEFAULT FALSE,
  read BOOLEAN DEFAULT FALSE
);

-- Admin Users
CREATE TABLE admin_users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email VARCHAR(255) UNIQUE NOT NULL,
  role VARCHAR(50) DEFAULT 'admin', -- admin, super_admin
  created_at TIMESTAMP DEFAULT NOW()
);

-- System Configuration
CREATE TABLE system_config (
  key VARCHAR(255) PRIMARY KEY,
  value JSONB NOT NULL,
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Admin Alerts Configuration
CREATE TABLE admin_alert_config (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  alert_type VARCHAR(50) NOT NULL, -- speed_limit
  threshold_value DECIMAL(10, 2),
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_teams_game_id ON teams(game_id);
CREATE INDEX idx_checkpoints_game_id ON checkpoints(game_id);
CREATE INDEX idx_team_achievements_team_id ON team_achievements(team_id);
CREATE INDEX idx_sent_messages_team_id ON sent_messages(team_id);
CREATE INDEX idx_sent_messages_sent_at ON sent_messages(sent_at);
CREATE INDEX idx_speed_records_team_id ON speed_records(team_id);
CREATE INDEX idx_automations_game_id ON automations(game_id);
```

### Application Architecture Details

#### Mobile App Structure (React Native)
```
src/
├── screens/
│   ├── CheckInScreen.tsx
│   ├── HomeScreen.tsx
│   ├── MessagesScreen.tsx
│   ├── RankingsScreen.tsx
│   └── RivalsScreen.tsx
├── components/
│   ├── CountdownTimer.tsx
│   ├── NotificationCard.tsx
│   ├── TeamSelector.tsx
│   └── CheckpointMap.tsx
├── services/
│   ├── supabaseClient.ts
│   ├── notificationService.ts
│   ├── locationService.ts
│   └── authService.ts
├── hooks/
│   ├── useGame.ts
│   ├── useTeam.ts
│   ├── useMessages.ts
│   └── useRealtime.ts
├── store/
│   ├── gameSlice.ts
│   ├── teamSlice.ts
│   └── messagesSlice.ts
├── navigation/
│   └── AppNavigator.tsx
└── utils/
    ├── dateTime.ts
    └── validators.ts
```

#### Admin Dashboard Structure (React)
```
admin/
├── src/
│   ├── pages/
│   │   ├── Dashboard.tsx
│   │   ├── Games/
│   │   │   ├── GameList.tsx
│   │   │   ├── GameForm.tsx
│   │   │   └── CheckpointManager.tsx
│   │   ├── Teams/
│   │   │   ├── TeamList.tsx
│   │   │   ├── TeamForm.tsx
│   │   │   └── TeamImport.tsx
│   │   ├── Messages/
│   │   │   ├── MessageTemplates.tsx
│   │   │   ├── SendMessage.tsx
│   │   │   └── MessageHistory.tsx
│   │   ├── Automations/
│   │   │   ├── AutomationList.tsx
│   │   │   └── AutomationForm.tsx
│   │   ├── Analytics/
│   │   │   ├── RankingsView.tsx
│   │   │   ├── SpeedTracking.tsx
│   │   │   └── ActivityLog.tsx
│   │   └── Settings/
│   │       ├── SystemConfig.tsx
│   │       └── AdminAlerts.tsx
│   ├── components/
│   │   ├── Layout/
│   │   ├── Forms/
│   │   └── Tables/
│   ├── services/
│   │   └── api.ts
│   └── hooks/
│       └── useAdmin.ts
```

#### Edge Functions (Supabase)
```
supabase/functions/
├── check-in/
│   └── index.ts           # Handle team check-in
├── send-notification/
│   └── index.ts           # Send push notifications
├── evaluate-automations/
│   └── index.ts           # Periodic automation evaluation
├── calculate-rankings/
│   └── index.ts           # Update team rankings
├── process-achievement/
│   └── index.ts           # Handle checkpoint achievements
└── speed-alert/
    └── index.ts           # Alert admins of speed violations
```

### Key Features Implementation

#### 1. Real-time Notifications
- Use Supabase real-time subscriptions for data updates
- FCM for push notifications when app is in background
- Local notifications for countdown timers

#### 2. Automation Engine
- Scheduled Edge Function runs every minute
- Evaluates automation rules against current game state
- Rate limiting using sent_messages table
- Event-driven triggers on achievements

#### 3. Offline Support
- Use React Query for caching
- Store critical data locally using AsyncStorage
- Queue messages for sending when online
- Sync on reconnection

#### 4. Security
- Row Level Security (RLS) policies in Supabase
- Teams can only read their own data and rival data
- Admins have elevated permissions
- JWT tokens for authentication
- API rate limiting

### Deployment Strategy

#### Phase 1: MVP (3-4 months)
- Basic check-in functionality
- Manual message sending
- Simple countdown notifications
- Team and game management

#### Phase 2: Automation (2-3 months)
- Full automation system
- Advanced triggers
- Statistics and rankings
- Rival tracking

#### Phase 3: Enhancement (Ongoing)
- Weather integration
- Advanced analytics
- Performance optimization
- Additional features based on user feedback

### Scalability Considerations

- Supabase can handle 100-1000 concurrent users easily
- FCM handles notification delivery at scale
- PostgreSQL can scale vertically
- Can add read replicas if needed
- Edge Functions scale automatically

### Cost Estimation (Annual)

**Serverless Option (Supabase + FCM)**
- Supabase Pro: $25/month = $300/year
- Firebase (FCM): Free tier likely sufficient
- Vercel/Netlify: Free tier for admin
- Expo: Free
- Total: ~$300-500/year

**Traditional Hosting**
- VPS (DigitalOcean): $20-40/month
- Database: Included or $15/month
- Total: ~$240-660/year

### Monitoring and Observability

- Supabase Dashboard for database monitoring
- Sentry for error tracking
- Custom logging in Edge Functions
- FCM delivery reports
- Admin dashboard analytics

## Alternative Architectures Comparison

| Criteria | Monolithic | Microservices | Serverless |
|----------|-----------|---------------|------------|
| Development Speed | Fast | Slow | Fast |
| Operational Complexity | Low | High | Low |
| Scalability | Vertical | Horizontal | Auto |
| Cost (Initial) | Low | High | Very Low |
| Cost (Scale) | Medium | High | Medium |
| Team Size Required | Small | Large | Small |
| Time to Market | Fast | Slow | Fast |
| **Recommendation** | Good for MVP | Overkill | **Best Choice** |

## Conclusion

**Recommended Architecture: Serverless (Option 3) with Supabase**

This architecture provides:
1. **Fast development** - Leverage BaaS features
2. **Real-time capabilities** - Built-in subscriptions
3. **Scalability** - Automatic scaling
4. **Cost-effective** - Pay per use, low initial cost
5. **Modern stack** - React Native, React, TypeScript, PostgreSQL
6. **Reduced ops burden** - Managed infrastructure
7. **Good migration path** - Can self-host Supabase if needed

The serverless architecture is ideal for the Vlettenrally app because:
- Event has ~100-500 concurrent users (within Supabase capacity)
- Real-time features are crucial (notifications, rankings)
- Small team needs to move fast
- Budget is likely constrained
- Seasonal usage pattern benefits from pay-per-use pricing

## Next Steps

1. **Set up Supabase project**
2. **Create database schema**
3. **Develop mobile app MVP** (Check-in + Messages)
4. **Build admin dashboard** (Game & Team management)
5. **Implement automation system**
6. **Testing and refinement**
7. **Deploy and monitor**

## Appendix: Technology Alternatives

### Database
- **PostgreSQL (Supabase)**: Recommended - Relational, powerful, open-source
- **Firestore (Firebase)**: Alternative - Document-based, real-time, easy to use
- **MongoDB**: Not recommended - Overkill for this use case

### Mobile Framework
- **React Native + Expo**: Recommended - Cross-platform, fast development
- **Flutter**: Alternative - Good performance, different ecosystem
- **Native (Swift/Kotlin)**: Not recommended - Double development effort

### Backend
- **Supabase**: Recommended - Open-source, PostgreSQL, feature-rich
- **Firebase**: Alternative - More mature, Google ecosystem
- **Custom Node.js**: Not recommended - More work, more maintenance

---

*Document Version: 1.0*  
*Last Updated: 2026-01-13*  
*Author: Architecture Team*
