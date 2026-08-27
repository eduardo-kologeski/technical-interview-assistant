import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export type ProficiencyLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export interface TechnicalSkill {
  id: number;
  name: string;
  proficiencyLevel: ProficiencyLevel;
}
export interface Availability {
  id: number;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
}
export interface Profile {
  id: number;
  name: string;
  email: string;
  technicalSkills: TechnicalSkill[];
  availability: Availability[];
}
export interface RequirementMatch {
  name: string;
  matched: boolean;
  proficiencyLevel: ProficiencyLevel | null;
}
export interface InterviewerResult {
  id: number;
  name: string;
  allSkills: TechnicalSkill[];
  availability: Availability[];
  requirements: string[];
  requirementMatches: RequirementMatch[];
  matchedCount: number;
  requirementCount: number;
}

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly profileUrl = 'http://localhost:8081/api/profile';

  getProfile() {
    return this.http.get<Profile>(this.profileUrl);
  }
  updateProfile(value: Pick<Profile, 'name' | 'email'>) {
    return this.http.put<Profile>(this.profileUrl, value);
  }
  addSkill(name: string, proficiencyLevel: ProficiencyLevel) {
    return this.http.post<TechnicalSkill>(`${this.profileUrl}/technical-skills`, {
      name,
      proficiencyLevel,
    });
  }
  updateSkill(id: number, name: string, proficiencyLevel: ProficiencyLevel) {
    return this.http.put<TechnicalSkill>(`${this.profileUrl}/technical-skills/${id}`, {
      name,
      proficiencyLevel,
    });
  }
  removeSkill(id: number) {
    return this.http.delete<void>(`${this.profileUrl}/technical-skills/${id}`);
  }
  addAvailability(dayOfWeek: string, startTime: string, endTime: string) {
    return this.http.post<Availability>(`${this.profileUrl}/availability`, {
      dayOfWeek,
      startTime,
      endTime,
    });
  }
  updateAvailability(id: number, dayOfWeek: string, startTime: string, endTime: string) {
    return this.http.put<Availability>(`${this.profileUrl}/availability/${id}`, {
      dayOfWeek,
      startTime,
      endTime,
    });
  }
  removeAvailability(id: number) {
    return this.http.delete<void>(`${this.profileUrl}/availability/${id}`);
  }
  searchInterviewers(filters: {
    vacancyId?: string;
    technology?: string;
    day?: string;
    time?: string;
  }) {
    let params = new HttpParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value) params = params.set(key, value);
    });
    return this.http.get<InterviewerResult[]>('http://localhost:8081/api/interviewers', { params });
  }
}
