# Vlettenrally App - Architecture Document

## Executive Summary

This document outlines the architecture for the Vlettenrally App, a comprehensive responsive web application for managing and participating in Dutch scouting boat rally events. The app serves two primary user groups: participants (teams on boats) and administrators (rally organizers). The application uses WebSockets for real-time communication and is designed to support up to 500 concurrent participants.

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

### Option 1: Self-Hosted Web Application (Recommended)

```
┌─────────────────────────────────────────────────────────────────┐
│                    Responsive Web Frontend                       │
│  ┌──────────────────────┐          ┌──────────────────────────┐ │
│  │  Participant UI      │          │  Admin UI                │ │
│  │  - React + TypeScript│          │  - React + TypeScript    │ │
│  │  - Responsive Design │          │  - Admin Dashboard       │ │
│  │  - Progressive Web   │          │  - Configuration         │ │
│  │    App (PWA)         │          │  - Management            │ │
│  └──────────────────────┘          └──────────────────────────┘ │
│           WebSocket Connection ←──────────────────→              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Backend Server                              │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  API Layer (REST + WebSocket)                               ││
│  │  - Express.js or Fastify                                    ││
│  │  - Socket.IO or ws for WebSocket                            ││
│  │  - JWT Authentication                                       ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Business Logic Layer                                       ││
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐ ││
│  │  │ Game Service │  │ Team Service │  │ Message Service  │ ││
│  │  │              │  │              │  │ - Broadcasting   │ ││
│  │  │              │  │              │  │ - Rate Limiting  │ ││
│  │  └──────────────┘  └──────────────┘  └──────────────────┘ ││
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐ ││
│  │  │ Automation   │  │ Analytics    │  │ Notification     │ ││
│  │  │ Engine       │  │ Service      │  │ Service          │ ││
│  │  │ - Scheduler  │  │ - Rankings   │  │ - Rules Engine   │ ││
│  │  └──────────────┘  └──────────────┘  └──────────────────┘ ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Data Layer                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  PostgreSQL  │  │    Redis     │  │  File Storage        │ │
│  │  - Games     │  │  - Sessions  │  │  - Local/S3          │ │
│  │  - Teams     │  │  - Cache     │  │  - Documents         │ │
│  │  - Messages  │  │  - Pub/Sub   │  │  - Exports           │ │
│  │  - Audit Log │  │  - WebSocket │  │                      │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

**Pros:**
- No vendor lock-in - full control over infrastructure
- Single codebase, easier to maintain
- Can be self-hosted on any VPS
- WebSocket for real-time updates (no dependency on external push services)
- Suitable for 500 concurrent users
- Lower long-term costs
- Progressive Web App provides app-like experience
- Can run offline with service workers

**Cons:**
- Requires server management
- Manual scaling if needed
- Must implement own monitoring

### Option 2: Containerized Microservices Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend Layer                           │
│  ┌──────────────────────┐          ┌──────────────────────────┐ │
│  │  Participant Web App │          │  Admin Dashboard         │ │
│  │  - React + TypeScript│          │  - React + TypeScript    │ │
│  │  - Responsive Design │          │  - Material-UI           │ │
│  │  - PWA               │          │  - Advanced Features     │ │
│  └──────────────────────┘          └──────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway / Reverse Proxy                   │
│                    - Nginx or Traefik                            │
│                    - Authentication                              │
│                    - Load Balancing                              │
│                    - WebSocket Upgrade                           │
└─────────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┼─────────────────┐
            ▼                 ▼                 ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────┐
│  Game Service    │ │  Team Service    │ │ WebSocket Service    │
│  - REST API      │ │  - REST API      │ │  - Socket.IO         │
│  - Node.js       │ │  - Node.js       │ │  - Real-time Events  │
│  - Docker        │ │  - Docker        │ │  - Docker            │
└──────────────────┘ └──────────────────┘ └──────────────────────┘
                                                     │
            ┌────────────────────────────────────────┤
            ▼                 ▼                      ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────┐
│ Message Service  │ │ Automation       │ │  Analytics Service   │
│ - Templates      │ │ Service          │ │  - Statistics        │
│  - Node.js       │ │  - Scheduler     │ │  - Rankings          │
│  - Docker        │ │  - Docker        │ │  - Docker            │
└──────────────────┘ └──────────────────┘ └──────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Shared Infrastructure                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  PostgreSQL  │  │    Redis     │  │  Message Queue       │ │
│  │  - Docker    │  │  - Docker    │  │  - RabbitMQ/Redis    │ │
│  │  - Primary   │  │  - Cache     │  │  - Docker            │ │
│  │    Data      │  │  - Pub/Sub   │  │                      │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

**Pros:**
- Services can scale independently
- Fault isolation between components
- Technology flexibility per service
- Docker provides consistency across environments
- Can deploy on any Docker host
- No vendor lock-in

**Cons:**
- More complex to develop and maintain
- Requires orchestration (Docker Compose or Kubernetes)
- Higher operational overhead
- More moving parts to monitor
- Overkill for 500 concurrent users

### Option 3: Monorepo with Modular Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Single Monorepo Application                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Frontend (Shared Codebase)                                 ││
│  │  - React + TypeScript + Vite                                ││
│  │  - Responsive Design (Mobile & Desktop)                     ││
│  │  - Participant Views + Admin Views                          ││
│  │  - Progressive Web App (PWA)                                ││
│  │  - Socket.IO Client for real-time                           ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Backend (Single Node.js Process)                           ││
│  │  ┌──────────────────────────────────────────────────────┐  ││
│  │  │  API Layer                                           │  ││
│  │  │  - Express.js / Fastify                              │  ││
│  │  │  - REST endpoints                                    │  ││
│  │  │  - Socket.IO for WebSocket                           │  ││
│  │  │  - JWT Authentication                                │  ││
│  │  └──────────────────────────────────────────────────────┘  ││
│  │  ┌──────────────────────────────────────────────────────┐  ││
│  │  │  Modules (Logical Separation)                        │  ││
│  │  │  - games/    - teams/      - messages/               │  ││
│  │  │  - automations/  - analytics/  - notifications/      │  ││
│  │  │  Each module: routes, services, controllers          │  ││
│  │  └──────────────────────────────────────────────────────┘  ││
│  │  ┌──────────────────────────────────────────────────────┐  ││
│  │  │  Background Jobs                                     │  ││
│  │  │  - Node-cron for scheduling                          │  ││
│  │  │  - Automation evaluation                             │  ││
│  │  │  - Countdown timers                                  │  ││
│  │  └──────────────────────────────────────────────────────┘  ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Data Layer                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  PostgreSQL  │  │    Redis     │  │  Local Storage       │ │
│  │  - All data  │  │  - Sessions  │  │  - Uploads           │ │
│  │  - Prisma ORM│  │  - Cache     │  │  - Exports           │ │
│  │              │  │  - Pub/Sub   │  │                      │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

**Pros:**
- Simplest architecture to develop and deploy
- Single deployment unit
- Shared types and utilities between frontend and backend
- Easy to refactor and reorganize
- Perfect for 500 concurrent users
- Lower operational complexity
- No vendor lock-in
- Can upgrade to microservices later if needed

**Cons:**
- All components scale together
- Single point of failure (mitigated with PM2 or clustering)
- Requires restart for deployment

## Recommended Architecture: Self-Hosted Web Application (Option 1)

### Technology Stack

#### Frontend
- **Web Application**: React + TypeScript + Vite
  - Single responsive application for both participants and admins
  - Progressive Web App (PWA) for app-like experience on mobile
  - Works offline with service workers
  - Material-UI or Tailwind CSS for responsive design
  - Mobile-first design approach
  
#### Backend
- **Runtime**: Node.js with Express.js or Fastify
  - Lightweight and performant
  - Large ecosystem of packages
  - Good TypeScript support
  
- **Real-time Communication**: Socket.IO
  - WebSocket with fallback to polling
  - Room-based broadcasting
  - Automatic reconnection
  - Easy integration with Redis for scaling
  
- **Database ORM**: Prisma
  - Type-safe database access
  - Automatic migrations
  - Works with PostgreSQL
  - No vendor lock-in

#### Infrastructure
- **Database**: PostgreSQL
  - Open-source, battle-tested
  - Excellent for relational data
  - JSONB for flexible data
  - Can host anywhere
  
- **Cache/Session Store**: Redis
  - Fast in-memory storage
  - Pub/Sub for WebSocket scaling
  - Session management
  - Rate limiting
  
- **Process Manager**: PM2
  - Process clustering for reliability
  - Zero-downtime deployments
  - Monitoring and logging
  
- **Hosting Options**:
  - DigitalOcean Droplet ($20-40/month)
  - Hetzner Cloud (€5-20/month)
  - Any VPS with Docker support
  
- **Reverse Proxy**: Nginx or Caddy
  - SSL/TLS termination
  - Static file serving
  - WebSocket proxying
  - Load balancing if needed

#### Development Tools
- **Monorepo**: Turborepo or Nx
  - Shared types between frontend and backend
  - Fast builds
  - Code sharing
  
- **Testing**: Jest + Playwright
  - Unit tests for business logic
  - E2E tests for critical flows
  
- **Monitoring**: Self-hosted options
  - Grafana + Prometheus for metrics
  - Loki for logs
  - Or simple logging to files with log rotation

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

#### Project Structure (Monorepo)
```
vlettenrally-app/
├── packages/
│   ├── frontend/              # React web application
│   │   ├── src/
│   │   │   ├── pages/
│   │   │   │   ├── participant/
│   │   │   │   │   ├── CheckIn.tsx
│   │   │   │   │   ├── Dashboard.tsx
│   │   │   │   │   ├── Messages.tsx
│   │   │   │   │   ├── Rankings.tsx
│   │   │   │   │   └── Rivals.tsx
│   │   │   │   └── admin/
│   │   │   │       ├── Dashboard.tsx
│   │   │   │       ├── Games.tsx
│   │   │   │       ├── Teams.tsx
│   │   │   │       ├── Messages.tsx
│   │   │   │       ├── Automations.tsx
│   │   │   │       └── Analytics.tsx
│   │   │   ├── components/
│   │   │   │   ├── layout/
│   │   │   │   ├── forms/
│   │   │   │   ├── CountdownTimer.tsx
│   │   │   │   ├── NotificationCard.tsx
│   │   │   │   └── RankingTable.tsx
│   │   │   ├── hooks/
│   │   │   │   ├── useSocket.ts
│   │   │   │   ├── useAuth.ts
│   │   │   │   ├── useGame.ts
│   │   │   │   └── useRealtime.ts
│   │   │   ├── services/
│   │   │   │   ├── api.ts
│   │   │   │   ├── socket.ts
│   │   │   │   └── storage.ts
│   │   │   ├── store/
│   │   │   │   ├── authSlice.ts
│   │   │   │   ├── gameSlice.ts
│   │   │   │   └── messagesSlice.ts
│   │   │   └── utils/
│   │   │       ├── dateTime.ts
│   │   │       └── validators.ts
│   │   ├── public/
│   │   │   ├── manifest.json     # PWA manifest
│   │   │   └── service-worker.js  # Service worker for offline
│   │   ├── vite.config.ts
│   │   └── package.json
│   │
│   ├── backend/               # Node.js server
│   │   ├── src/
│   │   │   ├── modules/
│   │   │   │   ├── auth/
│   │   │   │   │   ├── auth.controller.ts
│   │   │   │   │   ├── auth.service.ts
│   │   │   │   │   └── auth.routes.ts
│   │   │   │   ├── games/
│   │   │   │   ├── teams/
│   │   │   │   ├── messages/
│   │   │   │   ├── automations/
│   │   │   │   ├── analytics/
│   │   │   │   └── notifications/
│   │   │   ├── websocket/
│   │   │   │   ├── socket.handler.ts
│   │   │   │   ├── rooms.service.ts
│   │   │   │   └── events.ts
│   │   │   ├── jobs/
│   │   │   │   ├── automation.job.ts
│   │   │   │   ├── countdown.job.ts
│   │   │   │   └── analytics.job.ts
│   │   │   ├── middleware/
│   │   │   │   ├── auth.middleware.ts
│   │   │   │   ├── validation.middleware.ts
│   │   │   │   └── rateLimit.middleware.ts
│   │   │   ├── utils/
│   │   │   ├── config/
│   │   │   └── server.ts
│   │   ├── prisma/
│   │   │   ├── schema.prisma
│   │   │   └── migrations/
│   │   └── package.json
│   │
│   └── shared/                # Shared types and utilities
│       ├── src/
│       │   ├── types/
│       │   │   ├── game.types.ts
│       │   │   ├── team.types.ts
│       │   │   ├── message.types.ts
│       │   │   └── api.types.ts
│       │   └── utils/
│       │       ├── constants.ts
│       │       └── validation.ts
│       └── package.json
│
├── docker-compose.yml         # Local development setup
├── .env.example
├── turbo.json                 # Turborepo config
└── package.json               # Root package.json
```

#### WebSocket Events Architecture
```typescript
// Client → Server Events
interface ClientToServerEvents {
  'join:game': (gameId: string) => void;
  'join:team': (teamId: string) => void;
  'location:update': (data: LocationUpdate) => void;
  'message:read': (messageId: string) => void;
}

// Server → Client Events
interface ServerToClientEvents {
  'message:new': (message: Message) => void;
  'countdown:update': (countdown: CountdownData) => void;
  'team:achievement': (achievement: Achievement) => void;
  'rival:update': (rivalData: RivalUpdate) => void;
  'ranking:update': (rankings: Ranking[]) => void;
  'game:status': (status: GameStatus) => void;
  'admin:alert': (alert: Alert) => void;
}

// Room Strategy
// - game:{gameId} - All participants in a game
// - team:{teamId} - Specific team members
// - admin:{gameId} - Admin users for a game
// - rival:{teamId}:{rivalTeamId} - Rival-specific updates
```

### Key Features Implementation

#### 1. Real-time Communication via WebSockets
- Socket.IO for bidirectional real-time communication
- Automatic reconnection handling
- Room-based broadcasting for efficiency
- Redis adapter for horizontal scaling (if needed)
- Fallback to long-polling for restrictive networks

#### 2. Progressive Web App (PWA)
- Service worker for offline functionality
- App manifest for "Add to Home Screen"
- Cache-first strategy for static assets
- Network-first for API calls with offline queue
- Background sync for pending actions

#### 3. Automation Engine
- Node-cron for scheduled jobs (runs every minute)
- Evaluates automation rules against current game state
- Rate limiting using database queries
- Event-driven triggers on achievements
- Configurable through admin UI

#### 4. Authentication & Authorization
- JWT tokens stored in httpOnly cookies
- Role-based access control (RBAC)
  - Participant: Read own team data, read rival data
  - Admin: Full access to game management
- Session management with Redis
- CSRF protection

#### 5. Responsive Design
- Mobile-first approach
- Breakpoints: Mobile (< 768px), Tablet (768-1024px), Desktop (> 1024px)
- Touch-friendly UI elements
- Different layouts for participant vs admin views
- Works on phones, tablets, and desktops

### Deployment Strategy

#### Development Environment
```bash
# Start all services with Docker Compose
docker-compose up -d

# Services:
# - PostgreSQL on port 5432
# - Redis on port 6379
# - Backend on port 3000
# - Frontend on port 5173 (Vite dev server)
```

#### Production Deployment Options

**Option A: Single VPS (Recommended for 500 users)**
- Hetzner Cloud CPX31 (4 vCPU, 8GB RAM): ~€12/month
- DigitalOcean Droplet (2 vCPU, 4GB RAM): $24/month
- Setup:
  1. Install Docker & Docker Compose
  2. Clone repository
  3. Configure environment variables
  4. Run `docker-compose -f docker-compose.prod.yml up -d`
  5. Setup Caddy/Nginx for SSL (Let's Encrypt)
  6. Configure firewall (UFW)

**Option B: Separate Database Hosting**
- App server: VPS as above
- Database: Managed PostgreSQL (DigitalOcean, Hetzner, etc.)
- Redis: Managed Redis or same VPS
- Benefits: Easier backups, automatic updates

**Docker Compose Production Setup**
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15-alpine
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: vlettenrally
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
  
  redis:
    image: redis:7-alpine
    volumes:
      - redis_data:/data
  
  backend:
    build: ./packages/backend
    depends_on:
      - postgres
      - redis
    environment:
      DATABASE_URL: ${DATABASE_URL}
      REDIS_URL: ${REDIS_URL}
      JWT_SECRET: ${JWT_SECRET}
    restart: unless-stopped
  
  frontend:
    build: ./packages/frontend
    depends_on:
      - backend
    restart: unless-stopped
  
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
    depends_on:
      - frontend
      - backend
```

#### Deployment Phases

**Phase 1: MVP (3-4 months)**
- Basic check-in functionality
- Manual message sending from admin
- Simple countdown notifications
- Team and game management
- Real-time message delivery via WebSocket

**Phase 2: Automation (2-3 months)**
- Full automation system
- Advanced trigger rules
- Statistics and rankings
- Rival tracking and notifications
- Speed monitoring and alerts

**Phase 3: Enhancement (Ongoing)**
- Weather API integration
- Advanced analytics and reporting
- Export functionality (CSV, PDF)
- Historical data visualization
- Performance optimization

### Scalability Considerations

**Current Design (500 concurrent users)**
- Single VPS with 4 vCPU, 8GB RAM can easily handle 500 concurrent WebSocket connections
- PostgreSQL can handle thousands of queries per second
- Redis provides sub-millisecond response times
- Nginx efficiently serves static files

**If Growth is Needed (> 1000 users)**
1. **Vertical Scaling**: Upgrade VPS to more CPU/RAM
2. **Horizontal Scaling**: 
   - Multiple backend instances behind load balancer
   - Redis adapter for Socket.IO to share connections
   - Read replicas for PostgreSQL
3. **CDN**: CloudFlare for static assets (free tier available)
4. **Database Optimization**: Connection pooling, query optimization, indexes

**Monitoring & Performance**
- PM2 for process monitoring and clustering
- Node.js built-in monitoring (CPU, memory)
- PostgreSQL slow query log
- Nginx access logs
- Custom metrics endpoint for Prometheus (optional)

### Cost Estimation (Annual)

**Self-Hosted Option (Recommended)**
- VPS (Hetzner CPX31): €12/month = €144/year
- Domain name: €10/year
- SSL: Free (Let's Encrypt)
- Backups: €5/month = €60/year
- **Total: ~€214/year (~$235 USD)**

**Alternative: Separate Database**
- VPS (smaller): €8/month = €96/year
- Managed PostgreSQL: €15/month = €180/year
- Domain & SSL: €10/year
- **Total: ~€286/year (~$315 USD)**

**Comparison with Serverless (Previous Recommendation)**
- Supabase Pro: $25/month = $300/year
- Less control, vendor lock-in
- Self-hosted is cheaper and more flexible

## Alternative Architectures Comparison

| Criteria | Self-Hosted Web App | Containerized Microservices | Monorepo Modular |
|----------|---------------------|----------------------------|------------------|
| Development Speed | Fast | Slow | Very Fast |
| Operational Complexity | Low-Medium | High | Low |
| Scalability | Vertical + Basic Horizontal | Full Horizontal | Vertical |
| Cost (Initial) | Low | Medium | Very Low |
| Cost (at 500 users) | ~€215/year | ~€400/year | ~€180/year |
| Team Size Required | Small (1-2) | Medium (3-5) | Small (1-2) |
| Time to Market | Fast | Slow | Very Fast |
| Vendor Lock-in | None | None | None |
| Real-time via WebSocket | Yes | Yes | Yes |
| Mobile Support | PWA | PWA | PWA |
| **Recommendation** | **Best Choice** | Overkill | Good for MVP |

### Why Self-Hosted Web App (Option 1) is Recommended

1. **No Vendor Lock-in**: Complete control over infrastructure
2. **WebSocket Real-time**: Native Socket.IO support without external services
3. **Cost-effective**: ~€215/year for 500 concurrent users
4. **PWA Benefits**: App-like experience without app store hassle
5. **Responsive Design**: Works on all devices (phones, tablets, desktops)
6. **Simple Deployment**: Single VPS or Docker Compose setup
7. **Easy to Maintain**: Monolithic but well-organized with modules
8. **Room to Grow**: Can scale to 1000+ users with minor adjustments

### Migration Path

If the application grows beyond expectations:
1. **Phase 1** (Current): Monolithic web app
2. **Phase 2** (If > 1000 users): Add load balancer, multiple backend instances
3. **Phase 3** (If > 5000 users): Split into microservices (already modular)

## Conclusion

**Recommended Architecture: Self-Hosted Web Application (Option 1)**

This architecture provides:
1. **No vendor lock-in** - Full control over all components
2. **WebSocket real-time** - Socket.IO for bidirectional communication
3. **Responsive web app** - PWA works on all devices without app stores
4. **Cost-effective** - ~€215/year for infrastructure
5. **Scalable enough** - Handles 500 concurrent users easily
6. **Modern stack** - React, TypeScript, Node.js, PostgreSQL, Redis
7. **Simple operations** - Single VPS deployment or Docker Compose
8. **Room to grow** - Can scale to 1000+ users with minor adjustments

The self-hosted web application is ideal for the Vlettenrally app because:
- No mobile app needed - responsive web works everywhere
- WebSocket provides real-time features without push notification services
- 500 concurrent users is well within capacity of a single VPS
- Avoids vendor lock-in (no Firebase, Supabase, or cloud-specific services)
- Small team can manage it easily
- Progressive Web App provides app-like experience
- Can be installed on home screen just like native apps
- Open-source stack ensures long-term viability

## Next Steps

1. **Set up development environment**
   - Initialize monorepo with Turborepo
   - Configure TypeScript and ESLint
   - Set up Docker Compose for local development

2. **Create database schema**
   - Set up Prisma
   - Define schema based on requirements
   - Create initial migrations

3. **Develop backend API**
   - Express.js server with TypeScript
   - Implement authentication (JWT)
   - Create REST endpoints for core features
   - Integrate Socket.IO for real-time

4. **Build frontend application**
   - React + TypeScript + Vite
   - Responsive layouts (mobile-first)
   - Socket.IO client integration
   - PWA configuration

5. **Implement core features**
   - Check-in system
   - Message broadcasting
   - Countdown timers
   - Game & team management

6. **Deploy to production**
   - Provision VPS
   - Configure Nginx/Caddy
   - Set up SSL certificates
   - Deploy with Docker Compose

7. **Monitor and iterate**
   - Set up basic monitoring
   - Gather user feedback
   - Implement automation system
   - Add analytics features

## Appendix: Technology Alternatives

### Frontend Framework
- **React + TypeScript**: Recommended - Popular, great ecosystem, TypeScript support
- **Vue.js**: Alternative - Simpler learning curve, good for small teams
- **Svelte**: Alternative - Excellent performance, smaller bundle size

### Backend Framework
- **Express.js**: Recommended - Mature, flexible, large ecosystem
- **Fastify**: Alternative - Better performance, modern design
- **NestJS**: Alternative - More structure, TypeScript-first, overkill for this size

### Database
- **PostgreSQL**: Recommended - Relational, JSONB for flexibility, open-source
- **MySQL/MariaDB**: Alternative - Similar features, slightly different syntax
- **MongoDB**: Not recommended - Overkill for structured data

### WebSocket Library
- **Socket.IO**: Recommended - Reliable, auto-reconnection, room support
- **ws**: Alternative - Lightweight, lower-level, more control needed
- **uWebSockets.js**: Alternative - Best performance, less features

### Hosting Provider (No Vendor Lock-in)
- **Hetzner Cloud**: Recommended - Best price/performance, EU-based
- **DigitalOcean**: Alternative - Good docs, slightly more expensive
- **Linode**: Alternative - Similar to DigitalOcean
- **OVH**: Alternative - European provider, competitive pricing

---

*Document Version: 2.0*  
*Last Updated: 2026-01-13*  
*Author: Architecture Team*  
*Updated: Based on feedback - Web app with WebSockets, no vendor lock-in*
