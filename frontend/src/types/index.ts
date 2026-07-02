// TypeScript mirrors of the backend DTOs.

export type Role = 'APPLICANT' | 'STAFF' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  name: string;
  role: Role;
  organization?: string | null;
  enabled: boolean;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Parcel {
  id: string;
  apn: string;
  address: string;
  zone?: string;
  generalPlanLandUse?: string;
  overlays: string[];
  hazardZones: string[];
  councilDistrict?: number;
  communityPlanArea?: string;
  latitude?: number;
  longitude?: number;
}

export interface DocRequirement {
  docKey: string;
  label: string;
  required?: boolean;
  description?: string;
}

export interface FormField {
  id: string;
  label: string;
  type: 'number' | 'boolean' | 'select' | 'text';
  required?: boolean;
  options?: string[];
  showIf?: string;
}

export interface PermitType {
  id: string;
  code: string;
  name: string;
  category: string;
  description?: string;
  formSchema: FormField[];
  requiredDocs: DocRequirement[];
  active: boolean;
}

export interface DocumentDto {
  id: string;
  originalName: string;
  fileType: string;
  sizeBytes: number;
  docCategory?: string;
  scanStatus: string;
  scanDetail?: string;
  version: number;
  extractedTextChars: number;
  uploadedAt: string;
}

export type ReadinessStatus =
  | 'READY_FOR_SUBMISSION' | 'REQUIRES_ATTENTION' | 'INCOMPLETE' | 'NOT_ASSESSED';

export interface Project {
  id: string;
  universalProjectId: string;
  title: string;
  permitTypeCode: string;
  projectScope?: string;
  intendedUse?: string;
  description?: string;
  address?: string;
  apn?: string;
  parcel?: Parcel | null;
  formData: Record<string, unknown>;
  status: string;
  currentReadinessScore?: number | null;
  currentReadinessStatus?: ReadinessStatus | null;
  usedAipPpc: boolean;
  ownerName?: string;
  ownerEmail?: string;
  submittedToEplanlaAt?: string | null;
  createdAt: string;
  updatedAt: string;
  documents: DocumentDto[];
}

export interface ProjectSummary {
  id: string;
  universalProjectId: string;
  title: string;
  permitTypeCode: string;
  address?: string;
  status: string;
  currentReadinessScore?: number | null;
  currentReadinessStatus?: ReadinessStatus | null;
  ownerName?: string;
  createdAt: string;
  updatedAt: string;
}

export type Severity = 'BLOCKING' | 'WARNING' | 'INFORMATIONAL';

export interface Finding {
  id: string;
  category: string;
  severity: Severity;
  title: string;
  description: string;
  codeReference?: string;
  codeUrl?: string;
  confidence: number;
  confidenceLevel: string;
  triggeringCondition?: string;
  assumptions?: string;
  recommendation?: string;
  source: string;
  ruleCode?: string;
  pageNumber?: number;
  staffDisposition: string;
  staffComment?: string;
  applicantFlagged: boolean;
  applicantFlagComment?: string;
  createdAt: string;
}

export interface Clearance {
  id: string;
  department: string;
  clearanceName: string;
  reason: string;
  confidence: number;
  confidenceLevel: string;
  submittalRequirements: string[];
  infoUrl?: string;
  source: string;
  staffDisposition: string;
  staffComment?: string;
  createdAt: string;
}

export interface Run {
  id: string;
  projectId: string;
  universalProjectId: string;
  status: string;
  readinessScore?: number | null;
  readinessStatus?: ReadinessStatus | null;
  summary?: string;
  findingCount: number;
  blockingCount: number;
  warningCount: number;
  infoCount: number;
  clearanceCount: number;
  processingMs?: number | null;
  aiProviderUsed?: string;
  aiModelUsed?: string;
  codeVersion?: string;
  triggeredBy: string;
  errorMessage?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export interface RunDetail {
  run: Run;
  findings: Finding[];
  clearances: Clearance[];
}

export interface Analytics {
  totalProjects: number;
  projectsUsingAipPpc: number;
  totalRuns: number;
  completedRuns: number;
  failedRuns: number;
  avgProcessingMs: number;
  pctWithinTarget: number;
  targetProcessingMs: number;
  totalFindings: number;
  totalClearances: number;
  avgReadinessScore: number;
  readyForSubmission: number;
  requiresAttention: number;
  incomplete: number;
  submittedToEplanla: number;
  openFeedback: number;
}

export interface ScreeningRule {
  id: string;
  code: string;
  name: string;
  category: string;
  severity: string;
  conditionJson: string;
  message: string;
  recommendation?: string;
  codeReference?: string;
  codeUrl?: string;
  confidence: number;
  appliesToPermitTypes?: string;
  priority: number;
  active: boolean;
  updatedAt: string;
}
